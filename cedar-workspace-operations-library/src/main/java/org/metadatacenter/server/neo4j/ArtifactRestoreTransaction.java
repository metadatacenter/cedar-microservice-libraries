package org.metadatacenter.server.neo4j;

import org.neo4j.driver.Transaction;
import java.util.Map;

/** Serializes graph completion with conditional document restoration. */
public final class ArtifactRestoreTransaction {
  private ArtifactRestoreTransaction() {}

  public static boolean lock(Transaction tx, String jobId) {
    var result = tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) "
        + "SET e.lockVersion = coalesce(e.lockVersion, 0) + 1 "
        + "WITH e WHERE e.jobId = $jobId RETURN e.jobId", Map.of("jobId", jobId));
    return result.hasNext();
  }

  /** Once restoration starts, its HTTP outcome can be uncertain; the original graph write must stop. */
  public static boolean lockForGraph(Transaction tx, String jobId) {
    if (!lock(tx, jobId)) { return false; }
    return !tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) "
        + "RETURN coalesce(e.restoreStarted, false) AS started", Map.of("jobId", jobId))
        .single().get("started").asBoolean();
  }

  public static void remove(Transaction tx, String jobId) {
    tx.run("MATCH (e:CedarArtifactRestoreOutbox {jobId: $jobId}) DELETE e",
        Map.of("jobId", jobId)).consume();
  }
}
