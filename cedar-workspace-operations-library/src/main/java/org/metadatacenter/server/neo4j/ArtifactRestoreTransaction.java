package org.metadatacenter.server.neo4j;

import org.neo4j.driver.Transaction;
import java.util.Map;

/** Serializes preparation, graph completion and conditional restoration per artifact. */
public final class ArtifactRestoreTransaction {
  private ArtifactRestoreTransaction() {}

  public enum GraphDecision { READY, SUPERSEDED, RESTORED, UNKNOWN }

  /** This node survives removal of an outbox job, so preparation never locks a disappearing job. */
  public static void lockResource(Transaction tx, String resourceId) {
    tx.run("MERGE (m:CedarArtifactRestoreLock {resourceId: $resourceId}) "
        + "SET m.lockVersion = coalesce(m.lockVersion, 0) + 1", Map.of("resourceId", resourceId)).consume();
  }

  public static boolean lock(Transaction tx, String jobId) {
    var found = tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) "
        + "RETURN properties(e) AS job", Map.of("jobId", jobId));
    if (!found.hasNext()) return false;
    var properties = found.single().get("job");
    if (properties.isNull() || !properties.asMap().containsKey("resourceId")) return false;
    lockResource(tx, properties.asMap().get("resourceId").toString());
    // Preparation may have superseded the job while this transaction waited for the stable lock.
    return tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) RETURN e.jobId",
        Map.of("jobId", jobId)).hasNext();
  }

  /** Once restoration starts, an uncertain HTTP outcome must prevent the original graph write. */
  public static boolean lockForGraph(Transaction tx, String jobId) {
    if (!lock(tx, jobId)) return false;
    return !tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) "
        + "RETURN coalesce(e.restoreStarted, false) AS started", Map.of("jobId", jobId))
        .single().get("started").asBoolean();
  }

  public static GraphDecision graphDecision(Transaction tx, String resourceId, String jobId) {
    lockResource(tx, resourceId);
    var pending = tx.run("MATCH (e:CedarArtifactRestoreOutbox {resourceId: $resourceId, jobId: $jobId}) "
        + "RETURN coalesce(e.restoreStarted, false) AS started",
        Map.of("resourceId", resourceId, "jobId", jobId));
    if (pending.hasNext()) return pending.single().get("started").asBoolean()
        ? GraphDecision.RESTORED : GraphDecision.READY;
    var outcome = tx.run("MATCH (o:CedarArtifactRestoreOutcome {resourceId: $resourceId, jobId: $jobId}) "
        + "RETURN o.state AS state", Map.of("resourceId", resourceId, "jobId", jobId));
    if (!outcome.hasNext()) return GraphDecision.UNKNOWN;
    return "SUPERSEDED".equals(outcome.single().get("state").asString(""))
        ? GraphDecision.SUPERSEDED : GraphDecision.RESTORED;
  }

  public static void restored(Transaction tx, String resourceId, String jobId) {
    tx.run("MERGE (o:CedarArtifactRestoreOutcome {jobId: $jobId}) "
        + "SET o.resourceId = $resourceId, o.state = 'RESTORED', o.createdAt = timestamp()",
        Map.of("resourceId", resourceId, "jobId", jobId)).consume();
  }

  public static void remove(Transaction tx, String jobId) {
    tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) DELETE e",
        Map.of("jobId", jobId)).consume();
  }
}
