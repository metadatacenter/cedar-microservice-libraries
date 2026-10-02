package org.metadatacenter.server.neo4j;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.model.CedarResourceType;
import org.neo4j.driver.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntFunction;

/** A create is registered in the graph or conditionally undone, never both. */
public final class ArtifactCreateCleanupOutbox implements AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ArtifactCreateCleanupOutbox.class);
  public record Job(String jobId, String resourceId, CedarResourceType resourceType, String etag) {}
  private final Driver driver;
  private final long delayMillis;

  public ArtifactCreateCleanupOutbox(CedarConfig config) {
    this(driver(config), 30_000);
  }

  public ArtifactCreateCleanupOutbox(Driver driver, long delayMillis) {
    this.driver = driver;
    this.delayMillis = delayMillis;
    VersionChainTransaction.initialize(driver);
    try (var session = driver.session()) {
      session.run("CREATE CONSTRAINT cedar_create_cleanup_job IF NOT EXISTS "
          + "FOR (j:CedarArtifactCreateCleanup) REQUIRE j.jobId IS UNIQUE").consume();
    }
  }

  private static Driver driver(CedarConfig config) {
    var neo = Neo4jConfig.fromCedarConfig(config);
    var settings = Config.builder();
    CedarTestRuntime.dependencyTimeoutMillis().ifPresent(timeout -> settings
        .withConnectionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withConnectionAcquisitionTimeout(timeout, TimeUnit.MILLISECONDS)
        .withMaxTransactionRetryTime(timeout, TimeUnit.MILLISECONDS));
    return GraphDatabase.driver(neo.getUri(), AuthTokens.basic(neo.getUserName(), neo.getUserPassword()), settings.build());
  }

  /** Fail closed before issuing a content write if its recovery intent cannot be persisted. */
  public String prepare(CedarResourceType type, String operation) {
    String jobId = UUID.randomUUID().toString();
    try (var session = driver.session()) {
      session.run("CREATE (j:CedarArtifactCreateCleanup {jobId:$job, resourceType:$type, operation:$operation, "
          + "createdAt:timestamp(), nextAttemptAt:timestamp()+$delay, attempts:0, parked:false})",
          Map.of("job", jobId, "type", type.getValue(), "operation", operation, "delay", delayMillis)).consume();
    }
    return jobId;
  }

  /** No destructive retry is possible until the exact created revision is known. */
  public void created(String jobId, String id, String etag) {
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      var args = new HashMap<String,Object>();
      args.put("job", jobId); args.put("id", id); args.put("etag", etag); args.put("delay", delayMillis);
      if (!tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) WHERE j.resourceId IS NULL "
          + "SET j.resourceId=$id, j.etag=$etag, j.parked=false, j.nextAttemptAt=timestamp()+$delay "
          + "REMOVE j.parkedReason RETURN j", args).hasNext()) {
        throw new IllegalStateException("Create recovery record is missing or already has a result: " + jobId);
      }
      tx.commit();
    }
  }

  /** Called inside the registration transaction, under the lifecycle lock. */
  public static void requireRegistration(Transaction tx, String jobId, String resourceId) {
    if (jobId != null && !tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job, resourceId:$id}) "
        + "WHERE coalesce(j.cleanupStarted,false)=false AND coalesce(j.parked,false)=false RETURN j",
        Map.of("job", jobId, "id", resourceId)).hasNext()) {
      throw new IllegalStateException("Create cleanup already ran or its outcome needs inspection: " + jobId);
    }
  }

  public static void registered(Transaction tx, String jobId) {
    if (jobId != null) tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) DELETE j",
        Map.of("job", jobId)).consume();
  }

  public List<String> pending(int limit) {
    try (var session = driver.session()) {
      return session.run("MATCH (j:CedarArtifactCreateCleanup) WHERE j.parked=false AND j.nextAttemptAt<=timestamp() "
          + "RETURN j.jobId AS id ORDER BY j.nextAttemptAt, j.createdAt LIMIT $limit", Map.of("limit",limit))
          .list(r -> r.get("id").asString());
    }
  }

  /** The lock spans the conditional DELETE and its acknowledgement, fencing late registration. */
  public void attempt(String jobId, ToIntFunction<Job> delete) {
    // Commit the fence BEFORE external I/O. If DELETE succeeds but this process dies before
    // acknowledging it, a late request still cannot register the now-deleted document.
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) WHERE j.resourceId IS NOT NULL "
          + "AND j.etag IS NOT NULL AND j.parked=false SET j.cleanupStarted=true", Map.of("job",jobId)).consume();
      tx.commit();
    }
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      var found = tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) RETURN properties(j) AS job",
          Map.of("job",jobId));
      if (!found.hasNext()) return; // Includes graph-commit success with a lost acknowledgement.
      var values = found.single().get("job").asMap();
      if (Boolean.TRUE.equals(values.get("parked"))) return;
      String id = (String)values.get("resourceId");
      String etag = (String)values.get("etag");
      if (id == null || etag == null || !etag.matches("\"[0-9]+\"")) {
        park(tx, jobId, "Create outcome or exact revision is unknown; inspect before cleanup");
      } else if (tx.run("MATCH (a:Artifact {_id:$id}) RETURN a", Map.of("id",id)).hasNext()) {
        park(tx, jobId, "Artifact is registered; inspect instead of deleting content");
      } else {
        tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) SET j.cleanupStarted=true",Map.of("job",jobId)).consume();
        int status;
        try {
          status = delete.applyAsInt(new Job(jobId, id, CedarResourceType.forValue((String)values.get("resourceType")), etag));
        } catch (RuntimeException failure) { status = 503; }
        if (status == 200 || status == 204 || status == 404) registered(tx, jobId);
        else if (status == 412) park(tx, jobId, "Content changed or was recreated; recorded revision must not be deleted");
        else if (status >= 400 && status < 500 && status != 408 && status != 429)
          park(tx, jobId, "Conditional cleanup refused with HTTP " + status);
        else {
          long attempts = ((Number)values.getOrDefault("attempts",0L)).longValue() + 1;
          tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) "
              + "SET j.attempts=$attempts, j.lastStatus=$status, j.nextAttemptAt=timestamp()+5000",
              Map.of("job",jobId,"attempts",attempts,"status",status)).consume();
          if (attempts >= 60) park(tx, jobId, "Cleanup exhausted sixty attempts; last HTTP status " + status);
        }
      }
      tx.commit();
    }
  }

  private static void park(Transaction tx, String jobId, String reason) {
    log.warn("Create cleanup {} requires inspection: {}", jobId, reason);
    tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) SET j.parked=true, j.parkedReason=$reason",
        Map.of("job",jobId,"reason",reason)).consume();
  }

  /** Only an explicit client rejection establishes that this attempt created no content. */
  public void rejected(String jobId, int status) {
    if (status < 400 || status >= 500 || status == 408) return;
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) WHERE j.resourceId IS NULL DELETE j",
          Map.of("job",jobId)).consume();
      tx.commit();
    }
  }

  @Override public void close() { driver.close(); }
}
