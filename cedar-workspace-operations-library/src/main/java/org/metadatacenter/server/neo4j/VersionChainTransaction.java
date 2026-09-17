package org.metadatacenter.server.neo4j;

import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.server.VersionTransitionConflictException;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Transaction;
import java.util.*;

/** Graph-side lifecycle invariants and the durable projection work committed with them. */
public final class VersionChainTransaction {
  private VersionChainTransaction() {}

  public static void initialize(Driver driver) {
    try (var session = driver.session()) {
      session.run("CREATE CONSTRAINT cedar_version_lock IF NOT EXISTS FOR (n:CedarVersionLock) REQUIRE n.id IS UNIQUE").consume();
      session.run("CREATE CONSTRAINT cedar_version_projection IF NOT EXISTS FOR (n:CedarVersionProjection) REQUIRE n.resourceId IS UNIQUE").consume();
      session.run("MERGE (:CedarVersionLock {id:'lifecycle'})").consume();
    }
  }

  /** Serializes the short graph transitions, including a successor check and its creation. */
  public static void lock(Transaction tx) {
    if (!tx.run("MATCH (n:CedarVersionLock {id:'lifecycle'}) SET n.revision=coalesce(n.revision,0)+1 RETURN n").hasNext()) {
      throw new IllegalStateException("Version lifecycle lock has not been initialized");
    }
  }

  public static List<Map<String,Object>> series(Transaction tx, String id) {
    return tx.run("MATCH (a:Artifact {`_id`:$id}) MATCH (a)-[:PREVIOUSVERSION*0..]-(v:Artifact) "
        + "RETURN DISTINCT properties(v) AS v", Map.of("id", id)).list(r -> r.get("v").asMap());
  }

  public static void requireDraftSource(Transaction tx, String id, String version) {
    var result = tx.run("MATCH (a:Artifact {`_id`:$id}) RETURN properties(a) AS a", Map.of("id", id));
    if (!result.hasNext()) throw new VersionTransitionConflictException("The draft source no longer exists");
    var source = result.next().get("a").asMap();
    if (!"bibo:published".equals(source.get("bibo_status"))) {
      throw new VersionTransitionConflictException("A draft requires a published source");
    }
    requireHead(tx, id);
    var oldVersion = ResourceVersion.forValueWithValidation((String) source.get("pav_version"));
    var newVersion = ResourceVersion.forValueWithValidation(version);
    if (!oldVersion.isValid() || !newVersion.isValid() || !oldVersion.isBefore(newVersion)) {
      throw new VersionTransitionConflictException("A draft version must exceed its published source");
    }
  }

  public static boolean requirePublish(Transaction tx, String id, String version) {
    requireHead(tx, id);
    var found = tx.run("MATCH (a:Artifact {_id:$id}) RETURN a.bibo_status AS status, a.pav_version AS version",
        Map.of("id", id));
    if (!found.hasNext()) return false;
    var current = found.next();
    if (!"bibo:draft".equals(current.get("status").asString())) {
      throw new VersionTransitionConflictException("Only a draft may be published");
    }
    var oldVersion = ResourceVersion.forValueWithValidation(current.get("version").asString());
    var newVersion = ResourceVersion.forValueWithValidation(version);
    if (!oldVersion.isValid() || !newVersion.isValid() || newVersion.isBefore(oldVersion)) {
      throw new VersionTransitionConflictException("Publication cannot lower the graph's version number");
    }
    return true;
  }

  public static void requireHead(Transaction tx, String id) {
    if (tx.run("MATCH (n:Artifact {`pav_previousVersion`:$id}) RETURN n LIMIT 1", Map.of("id",id)).hasNext()) {
      throw new VersionTransitionConflictException("Only the latest version may advance");
    }
    var ids = series(tx,id).stream().map(v -> (String)v.get("_id")).toList();
    if (tx.run("MATCH (j:CedarArtifactDeletionOutbox) WHERE j.resourceId IN $ids AND coalesce(j.parked,false)=false RETURN j LIMIT 1",
        Map.of("ids",ids)).hasNext()) {
      throw new VersionTransitionConflictException("A version deletion is pending completion");
    }
  }

  /** Recomputes every flag from the surviving series, without a folder or permission filter. */
  public static void reconcile(Transaction tx, String id) {
    var versions = series(tx,id);
    if (versions.isEmpty()) return;
    var drafts = versions.stream().filter(v -> "bibo:draft".equals(v.get("bibo_status"))).toList();
    if (drafts.size() > 1) throw new IllegalStateException("The version series contains multiple drafts");
    var published = versions.stream().filter(v -> "bibo:published".equals(v.get("bibo_status")))
        .max(Comparator.comparing(v -> ResourceVersion.forValueWithValidation((String)v.get("pav_version"))));
    String draft = drafts.isEmpty() ? null : (String)drafts.get(0).get("_id");
    String release = published.map(v -> (String)v.get("_id")).orElse(null);
    String latest = draft != null ? draft : release;
    for (var v : versions) {
      String vid = (String)v.get("_id");
      tx.run("MATCH (v:Artifact {`_id`:$id}) SET v.isLatestVersion=$latest, v.isLatestDraftVersion=$draft, "
          + "v.isLatestPublishedVersion=$published", Map.of("id",vid,"latest",vid.equals(latest),
          "draft",vid.equals(draft),"published",vid.equals(release))).consume();
      enqueue(tx,vid,false);
    }
  }

  public static void enqueue(Transaction tx, String id, boolean previousChanged) {
    tx.run("MERGE (j:CedarVersionProjection {resourceId:$id}) "
        + "SET j.syncPrevious=coalesce(j.syncPrevious,false) OR $previous, j.updatedAt=timestamp()",
        Map.of("id",id,"previous",previousChanged)).consume();
  }

  /** Reconnect both the property and relationship before removing the node, all in one commit. */
  public static void delete(Transaction tx, String id) {
    lock(tx);
    var found = tx.run("MATCH (a:Artifact {`_id`:$id}) RETURN properties(a) AS a",Map.of("id",id));
    if (!found.hasNext()) return;
    var current = found.next().get("a").asMap();
    var survivors = series(tx,id).stream().map(v -> (String)v.get("_id")).filter(v -> !v.equals(id)).toList();
    String previous = (String)current.get("pav_previousVersion");
    if (previous != null && !tx.run("MATCH (p:Artifact {`_id`:$id}) RETURN p",Map.of("id",previous)).hasNext()) previous=null;
    var successors = tx.run("MATCH (s:Artifact {`pav_previousVersion`:$id}) RETURN s.`_id` AS id",Map.of("id",id))
        .list(r -> r.get("id").asString());
    for (String successor: successors) {
      var args = new HashMap<String,Object>(); args.put("id",successor); args.put("previous",previous);
      tx.run("MATCH (s:Artifact {`_id`:$id}) OPTIONAL MATCH (s)-[r:PREVIOUSVERSION]->() DELETE r "
          + "SET s.`pav_previousVersion`=$previous",args).consume();
      if (previous != null) tx.run("MATCH (s:Artifact {`_id`:$id}), (p:Artifact {`_id`:$previous}) MERGE (s)-[:PREVIOUSVERSION]->(p)",args).consume();
      enqueue(tx,successor,true);
    }
    tx.run("MATCH (a:Artifact {`_id`:$id}) DETACH DELETE a",Map.of("id",id)).consume();
    tx.run("MATCH (j:CedarVersionProjection {resourceId:$id}) DELETE j",Map.of("id",id)).consume();
    if (!survivors.isEmpty()) reconcile(tx,survivors.get(0));
  }
}
