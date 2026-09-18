package org.metadatacenter.server.neo4j;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.neo4j.driver.*;
import org.neo4j.harness.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionChainTransactionTest {
  static Neo4j neo;
  static Driver driver;
  @BeforeAll static void start() {
    neo=Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    driver=GraphDatabase.driver(neo.boltURI(),AuthTokens.none());
    VersionChainTransaction.initialize(driver);
  }
  @AfterAll static void stop() { driver.close(); neo.close(); }
  @BeforeEach void clear() {
    try(var s=driver.session()) { s.run("MATCH (n) WHERE NOT n:CedarVersionLock DETACH DELETE n").consume(); }
  }
  static void chain(String tailStatus) {
    try(var s=driver.session()) {
      s.run("CREATE (a:Artifact {`_id`:'a',`pav_version`:'1.0.0',`bibo_status`:'bibo:published'}), "
          + "(b:Artifact {`_id`:'b',`pav_version`:'2.0.0',`bibo_status`:'bibo:published',`pav_previousVersion`:'a'}), "
          + "(c:Artifact {`_id`:'c',`pav_version`:'3.0.0',`bibo_status`:$status,`pav_previousVersion`:'b'}), "
          + "(c)-[:PREVIOUSVERSION]->(b)-[:PREVIOUSVERSION]->(a)",Map.of("status",tailStatus)).consume();
      s.writeTransaction(tx -> { VersionChainTransaction.lock(tx); VersionChainTransaction.reconcile(tx,"a"); return null; });
    }
  }
  static Map<String,Object> node(String id) {
    try(var s=driver.session()) { return s.run("MATCH (a:Artifact {`_id`:$id}) RETURN properties(a) AS a",Map.of("id",id)).single().get("a").asMap(); }
  }
  static void flags(String id, boolean latest, boolean draft, boolean published) {
    var a=node(id);
    assertEquals(latest,a.get("isLatestVersion"),id+" latest");
    assertEquals(draft,a.get("isLatestDraftVersion"),id+" draft");
    assertEquals(published,a.get("isLatestPublishedVersion"),id+" published");
  }
  @ParameterizedTest @CsvSource({"bibo:draft,a","bibo:draft,b","bibo:draft,c","bibo:published,a","bibo:published,b","bibo:published,c"})
  void deletingAnyPositionReconnectsHistoryAndRecomputesEveryFlag(String status,String deleted) {
    chain(status);
    try(var s=driver.session()) { s.writeTransaction(tx -> { VersionChainTransaction.delete(tx,deleted); return null; }); }
    if (deleted.equals("a")) {
      assertFalse(node("b").containsKey("pav_previousVersion"));
      flags("b",false,false,status.equals("bibo:draft"));
      flags("c",true,status.equals("bibo:draft"),status.equals("bibo:published"));
    } else if (deleted.equals("b")) {
      assertEquals("a",node("c").get("pav_previousVersion"));
      flags("a",false,false,status.equals("bibo:draft"));
      flags("c",true,status.equals("bibo:draft"),status.equals("bibo:published"));
    } else {
      flags("a",false,false,false); flags("b",true,false,true);
    }
    try(var s=driver.session()) {
      assertEquals(0,s.run("MATCH (a:Artifact {`pav_previousVersion`:$id}) RETURN count(a) AS n",Map.of("id",deleted)).single().get("n").asInt());
      var mismatches=s.run("MATCH (a:Artifact) OPTIONAL MATCH (a)-[:PREVIOUSVERSION]->(p) "
          + "WITH a,p WHERE coalesce(a.`pav_previousVersion`,'')<>coalesce(p.`_id`,'') RETURN count(a) AS n").single().get("n").asInt();
      assertEquals(0,mismatches);
      assertEquals(2,s.run("MATCH (j:CedarVersionProjection) RETURN count(j) AS n").single().get("n").asInt());
      if (!deleted.equals("c")) {
        assertTrue(s.run("MATCH (j:CedarVersionProjection {resourceId:$id}) RETURN j.syncPrevious AS sync",
            Map.of("id",deleted.equals("a")?"b":"c")).single().get("sync").asBoolean());
      }
    }
  }
  @Test void deletingTheWholeSeriesLeavesNoLatestOrProjection() {
    chain("bibo:draft");
    try(var s=driver.session()) {
      for (String id:List.of("b","a","c")) s.writeTransaction(tx -> { VersionChainTransaction.delete(tx,id); return null; });
      assertEquals(0,s.run("MATCH (n) WHERE n:Artifact OR n:CedarVersionProjection RETURN count(n) AS n").single().get("n").asInt());
    }
  }
  @Test void aGraphFailureRollsBackLinksFlagsAndProjectionTogether() {
    chain("bibo:draft");
    try(var s=driver.session();var tx=s.beginTransaction()) { VersionChainTransaction.delete(tx,"b"); tx.rollback(); }
    assertEquals("b",node("c").get("pav_previousVersion"));
    flags("b",false,false,true); flags("c",true,true,false);
  }
  @Test void concurrentDraftsHaveExactlyOneWinner() throws Exception {
    chain("bibo:published");
    var barrier=new CyclicBarrier(2); var pool=Executors.newFixedThreadPool(2);
    try {
      List<Future<Boolean>> results=new ArrayList<>();
      for (String id:List.of("d","e")) results.add(pool.submit(() -> {
        barrier.await();
        try(var s=driver.session()) {
          s.writeTransaction(tx -> {
            VersionChainTransaction.lock(tx);
            VersionChainTransaction.requireDraftSource(tx,"c","3.0.1");
            tx.run("MATCH (c:Artifact {`_id`:'c'}) CREATE (d:Artifact {`_id`:$id,`pav_version`:'3.0.1',`bibo_status`:'bibo:draft',`pav_previousVersion`:'c'})-[:PREVIOUSVERSION]->(c)",Map.of("id",id)).consume();
            VersionChainTransaction.reconcile(tx,id); return null;
          }); return true;
        } catch (IllegalStateException expected) { return false; }
      }));
      int winners=0; for(var r:results) if(r.get(20,TimeUnit.SECONDS)) winners++;
      assertEquals(1,winners); flags("c",false,false,true);
      try(var s=driver.session()) { assertEquals(1,s.run("MATCH (a:Artifact {`bibo_status`:'bibo:draft'}) RETURN count(a) AS n").single().get("n").asInt()); }
    } finally { pool.shutdownNow(); }
  }
  @Test void publicationRechecksTheGraphVersionInsideTheTransaction() {
    chain("bibo:draft");
    try (var s = driver.session()) {
      assertThrows(IllegalStateException.class, () -> s.writeTransaction(tx -> {
        VersionChainTransaction.lock(tx);
        return VersionChainTransaction.requirePublish(tx,"c","2.0.0");
      }));
      assertTrue(s.<Boolean>writeTransaction(tx -> {
        VersionChainTransaction.lock(tx);
        return VersionChainTransaction.requirePublish(tx,"c","3.0.0");
      }));
    }
  }
  @Test void aPendingDeletePreventsAdvancingAnyVersionInThatSeries() {
    chain("bibo:published");
    try(var s=driver.session()) {
      s.run("CREATE (:CedarArtifactDeletionOutbox {resourceId:'b'})").consume();
      assertThrows(IllegalStateException.class,() -> s.writeTransaction(tx -> { VersionChainTransaction.lock(tx); VersionChainTransaction.requireDraftSource(tx,"c","3.0.1"); return null; }));
    }
  }
}
