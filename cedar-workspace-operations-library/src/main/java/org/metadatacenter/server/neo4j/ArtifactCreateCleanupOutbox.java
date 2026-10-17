package org.metadatacenter.server.neo4j;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.model.CedarResourceType;
import org.neo4j.driver.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/** A create is registered in the graph or conditionally undone, never both. */
public final class ArtifactCreateCleanupOutbox implements AutoCloseable {
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ArtifactCreateCleanupOutbox.class);
  public record Job(String jobId, String resourceId, CedarResourceType resourceType, String etag) {}

  /** What reading a job's identity on the artifact server found there. */
  public sealed interface Stored {
    /** A document at this exact revision, quoted as a conditional delete names it. */
    record Revision(String etag) implements Stored {}
    /** A document the answer gave no exact revision for, so no conditional delete can name it. */
    record Unversioned() implements Stored {}
    /** No document. */
    record Nothing() implements Stored {}
    /** An answer that settles nothing, carrying its HTTP status, or 503 when no answer came. */
    record Unknown(int status) implements Stored {}
  }

  /** The wait before the relay first attempts a job, and between two reads of an identity. */
  public static final long DELAY_MILLIS = 30_000;

  /**
   * How long the relay reads the identity of a create whose outcome is unknown.
   *
   * <p>The artifact server sets no deadline on a request it has accepted, so a create whose caller
   * gave up can still be stored long afterwards. Its MongoDB client allows each round trip 220 s:
   * 30 s to select a server, 120 s for a pooled connection, 10 s to connect and 60 s for each read.
   * An instance create makes six round trips before its insert, and the insert is sent within 160 s,
   * so the insert can reach MongoDB about 25 minutes after the request arrived. The caller's own
   * timeouts end its wait after about 22 s and bound nothing on the server. Thirty minutes from the
   * job's creation outlasts every write the job can still be waiting on.
   */
  public static final long RECHECK_MILLIS = 30 * 60_000;

  private static final int MAX_ATTEMPTS = 60;
  private final Driver driver;
  private final long delayMillis;
  private final long recheckMillis;

  public ArtifactCreateCleanupOutbox(CedarConfig config) {
    this(driver(config), DELAY_MILLIS, RECHECK_MILLIS);
  }

  public ArtifactCreateCleanupOutbox(Driver driver, long delayMillis) {
    this(driver, delayMillis, RECHECK_MILLIS);
  }

  public ArtifactCreateCleanupOutbox(Driver driver, long delayMillis, long recheckMillis) {
    this.driver = driver;
    this.delayMillis = delayMillis;
    this.recheckMillis = recheckMillis;
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

  /**
   * Records a create's intent as {@link #prepare(CedarResourceType, String)} does, for a create at an
   * identity its caller chose. The job holds the identity from before the content write, so a document
   * stored there after the create's answer was lost can still be found. Until that outcome is known, the relay reads the
   * identity for the re-check window, {@link #RECHECK_MILLIS} unless the outbox was given another.
   */
  public String prepare(CedarResourceType type, String operation, String resourceId) {
    if (resourceId == null) return prepare(type, operation);
    String jobId = UUID.randomUUID().toString();
    try (var session = driver.session()) {
      session.run("CREATE (j:CedarArtifactCreateCleanup {jobId:$job, resourceType:$type, operation:$operation, "
          + "resourceId:$id, createdAt:timestamp(), nextAttemptAt:timestamp()+$delay, "
          + "recheckUntil:timestamp()+$recheck, attempts:0, parked:false})",
          Map.of("job", jobId, "type", type.getValue(), "operation", operation, "id", resourceId,
              "delay", delayMillis, "recheck", recheckMillis)).consume();
    }
    return jobId;
  }

  /** No destructive retry is possible until the exact created revision is known. */
  public void created(String jobId, String id, String etag) {
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      var args = new HashMap<String,Object>();
      args.put("job", jobId); args.put("id", id); args.put("etag", etag); args.put("delay", delayMillis);
      // A job that holds its identity from the start takes the outcome only for that identity.
      if (!tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) WHERE j.resourceId IS NULL "
          + "OR (j.resourceId=$id AND j.etag IS NULL AND j.recheckUntil IS NOT NULL) "
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

  /**
   * The lock spans the conditional DELETE and its acknowledgement, fencing late registration. A job
   * that holds an identity but no revision is parked here, since nothing can read the identity.
   */
  public void attempt(String jobId, ToIntFunction<Job> delete) {
    attempt(jobId, null, delete);
  }

  /**
   * Attempts a job as {@link #attempt(String, ToIntFunction)} does, and reads the identity of a job that
   * holds one but no revision. A document found there that the graph has not registered becomes the
   * job's to remove at the revision just read. Its deletion then proceeds as any other job's does.
   */
  public void attempt(String jobId, Function<Job, Stored> read, ToIntFunction<Job> delete) {
    if (read != null && !recheck(jobId, read)) return;
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
          if (attempts >= MAX_ATTEMPTS) park(tx, jobId, "Cleanup exhausted sixty attempts; last HTTP status " + status);
        }
      }
      tx.commit();
    }
  }

  /**
   * Reads the identity of a job whose create has no known outcome, and decides what the answer
   * allows. Returns true when the attempt continues as it does for a job with a known revision, which
   * is the case for a job that does not await a read of its identity and for one whose document has
   * just been found and recorded.
   *
   * <p>The read happens outside the lifecycle lock. Every decision is taken under it, against the
   * graph and the jobs as they stand after the read, so a registration that lands while the read is
   * in flight is still seen. A document is taken as the job's only after every job that holds the
   * same identity has been fenced in the same transaction. That transaction commits before the
   * deletion is sent, so neither this create nor a retry at its identity can register a document
   * the relay is about to delete.
   */
  private boolean recheck(String jobId, Function<Job, Stored> read) {
    Map<String,Object> values;
    long readAt;
    try (var session = driver.session()) {
      var found = session.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) "
          + "RETURN properties(j) AS job, timestamp() AS now", Map.of("job", jobId));
      if (!found.hasNext()) return false;
      var record = found.single();
      values = record.get("job").asMap();
      readAt = record.get("now").asLong();
    }
    if (!awaitsRecheck(values)) return true;
    String id = (String)values.get("resourceId");
    // The time is taken before the read, so a read that finds nothing after the window proves that no
    // write is still coming.
    boolean windowOver = readAt >= ((Number)values.get("recheckUntil")).longValue();
    Stored stored;
    try {
      stored = Objects.requireNonNullElse(read.apply(new Job(jobId, id,
          CedarResourceType.forValue((String)values.get("resourceType")), null)), new Stored.Unknown(503));
    } catch (RuntimeException failure) { stored = new Stored.Unknown(503); }
    try (var session = driver.session(); var tx = session.beginTransaction()) {
      VersionChainTransaction.lock(tx);
      var found = tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) RETURN properties(j) AS job",
          Map.of("job", jobId));
      if (!found.hasNext()) return false;
      values = found.single().get("job").asMap();
      if (!awaitsRecheck(values)) return true;
      boolean recorded = false;
      if (tx.run("MATCH (a:Artifact {_id:$id}) RETURN a", Map.of("id", id)).hasNext()) {
        // The graph registered the identity, so its document is never deleted. A write this job still
        // awaits is refused while that document is there.
        retireOrRecheck(tx, jobId, id, windowOver);
      } else if (stored instanceof Stored.Nothing) {
        retireOrRecheck(tx, jobId, id, windowOver);
      } else if (stored instanceof Stored.Unversioned) {
        park(tx, jobId, "A document is stored at " + id + " without an exact revision; inspect before cleanup");
      } else if (stored instanceof Stored.Unknown unknown) {
        long attempts = ((Number)values.getOrDefault("attempts",0L)).longValue() + 1;
        tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) "
            + "SET j.attempts=$attempts, j.lastStatus=$status, j.nextAttemptAt=timestamp()+5000",
            Map.of("job",jobId,"attempts",attempts,"status",unknown.status())).consume();
        if (windowOver && attempts >= MAX_ATTEMPTS) {
          park(tx, jobId, "The identity " + id + " could not be read; last HTTP status " + unknown.status());
        }
      } else if (stored instanceof Stored.Revision revision) {
        if (tx.run("MATCH (j:CedarArtifactCreateCleanup {resourceId:$id, etag:$etag}) WHERE j.jobId<>$job "
            + "RETURN j LIMIT 1", Map.of("id", id, "etag", revision.etag(), "job", jobId)).hasNext()) {
          // Another create recorded this revision as its own, and registers or removes it itself.
          retireOrRecheck(tx, jobId, id, windowOver);
        } else {
          tx.run("MATCH (j:CedarArtifactCreateCleanup {resourceId:$id}) SET j.cleanupStarted=true",
              Map.of("id", id)).consume();
          tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) SET j.etag=$etag, j.attempts=0 "
              + "REMOVE j.lastStatus", Map.of("job", jobId, "etag", revision.etag())).consume();
          log.warn("Create cleanup {} found an unregistered document at {}, revision {}; removing it",
              jobId, id, revision.etag());
          recorded = true;
        }
      }
      tx.commit();
      return recorded;
    }
  }

  /** Whether a job held its identity from before the write and has learnt no revision for it since. */
  private static boolean awaitsRecheck(Map<String,Object> job) {
    return !Boolean.TRUE.equals(job.get("parked")) && job.get("resourceId") != null && job.get("etag") == null
        && job.get("recheckUntil") != null;
  }

  private void retireOrRecheck(Transaction tx, String jobId, String id, boolean windowOver) {
    if (windowOver) {
      log.info("Create cleanup {} retired: no write can still land at {}, and nothing there is left to remove",
          jobId, id);
      registered(tx, jobId);
    } else {
      tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) SET j.nextAttemptAt=timestamp()+$delay",
          Map.of("job", jobId, "delay", delayMillis)).consume();
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
      tx.run("MATCH (j:CedarArtifactCreateCleanup {jobId:$job}) WHERE j.resourceId IS NULL "
          + "OR (j.etag IS NULL AND j.recheckUntil IS NOT NULL) DELETE j", Map.of("job",jobId)).consume();
      tx.commit();
    }
  }

  @Override public void close() { driver.close(); }
}
