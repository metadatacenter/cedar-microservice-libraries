package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResourcePermission;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;
import org.neo4j.driver.*;
import org.neo4j.harness.*;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Executes the real registration and transfer queries against embedded Neo4j. */
class InstanceCloneOwnershipTest {
  static Neo4j neo;
  static Driver database;
  static final CedarTemplateInstanceId SOURCE = CedarTemplateInstanceId.build("source");
  static final CedarFolderId DESTINATION = CedarFolderId.build("destination");
  static final CedarUserId OWNER = CedarUserId.build("owner");
  static final CedarUserId NEXT_OWNER = CedarUserId.build("next-owner");
  @BeforeAll static void start() {
    neo = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    database = GraphDatabase.driver(neo.boltURI(), AuthTokens.none());
  }
  @AfterAll static void stop() { database.close(); neo.close(); }
  @BeforeEach void seed() {
    try (var session = database.session()) {
      session.run("MATCH (n) DETACH DELETE n").consume();
      session.run("CREATE (u:User:Resource {_id:'owner'}), (v:User:Resource {_id:'next-owner'}), "
          + "(s:Artifact:Instance:FileSystemResource:Resource {_id:'source'}), "
          + "(p:Folder:FileSystemResource:Resource {_id:'destination'}), (u)-[:OWNS]->(s), (u)-[:OWNS]->(p)").consume();
    }
  }
  static class Repository extends Neo4JProxyArtifact {
    final CountDownLatch entered = new CountDownLatch(1);
    Repository() { super(mock(Neo4JProxies.class), mock(CedarConfig.class)); }
    @Override protected void initializeVersioning() { org.metadatacenter.server.neo4j.VersionChainTransaction.initialize(database); }
    @Override protected <T> T executeInWriteTransaction(Function<Transaction,T> work, String description) {
      entered.countDown();
      try (var session = database.session()) { return session.writeTransaction(work::apply); }
    }
  }
  static FolderServerArtifact register(Repository repository) {
    var copy = new FolderServerInstance();
    copy.setId("clone"); copy.setName("Cloned instance"); copy.setCreatedByTotal(OWNER);
    copy.setIsBasedOn(CedarTemplateId.build("new-template"));
    return repository.createInstanceCloneAsChildOfId(copy, SOURCE, DESTINATION, OWNER);
  }
  static void transfer(Transaction tx, String id) {
    CedarFilesystemResourceId resource = id.equals("source") ? SOURCE : DESTINATION;
    var lock = new CypherQueryWithParameters(CypherQueryBuilderFilesystemResourcePermission.lockPermissions(),
        CypherParamBuilderFilesystemResource.matchFilesystemResource(resource));
    long revision = tx.run(lock.getRunnableQuery(), lock.getParameterMap()).single().get("revision").asLong();
    var params = CypherParamBuilderFilesystemResource.matchFilesystemResourceAndUser(resource, NEXT_OWNER);
    params.put(ParameterPlaceholder.OWNER_ID, OWNER);
    params.put(ParameterPlaceholder.CURRENT_REVISION, revision);
    var query = new CypherQueryWithParameters(CypherQueryBuilderFilesystemResourcePermission.transferOwnership(), params);
    tx.run(query.getRunnableQuery(), query.getParameterMap()).consume();
  }
  static long copies() {
    try (var session = database.session()) {
      return session.run("MATCH (n:Artifact {_id:'clone'}) RETURN count(n) AS n").single().get("n").asLong();
    }
  }
  @ParameterizedTest @ValueSource(booleans = {false, true})
  void cloneRegistrationRetiresCleanupOnlyIfOwnershipStillAllowsIt(boolean transferred) {
    try (var outbox = new org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox(
        GraphDatabase.driver(neo.boltURI(), AuthTokens.none()), 0)) {
      String job = outbox.prepare(org.metadatacenter.model.CedarResourceType.INSTANCE, "worker-clone");
      outbox.created(job, "clone", "\"7\"");
      if (transferred) {
        try (var session = database.session()) {
          session.writeTransaction(tx -> { transfer(tx, "destination"); return null; });
        }
      }
      var copy = new FolderServerInstance();
      copy.setId("clone"); copy.setName("Cloned instance"); copy.setCreatedByTotal(OWNER);
      copy.setIsBasedOn(CedarTemplateId.build("new-template"));
      var result = new Repository().createInstanceCloneAsChildOfId(copy, SOURCE, DESTINATION, OWNER, job);
      if (transferred) {
        assertNull(result);
        assertEquals(java.util.List.of(job), outbox.pending(10));
        outbox.attempt(job, pending -> { assertEquals("\"7\"", pending.etag()); return 204; });
      } else {
        assertNotNull(result);
        outbox.attempt(job, pending -> fail("Registered clones must never be compensated"));
      }
      assertTrue(outbox.pending(10).isEmpty());
      assertEquals(transferred ? 0 : 1, copies());
    }
  }

  @ParameterizedTest @ValueSource(strings = {"source", "destination"})
  void transferAfterEnumerationPreventsACloneForTheFormerOwner(String id) {
    try (var session = database.session()) { session.writeTransaction(tx -> { transfer(tx,id); return null; }); }
    assertNull(register(new Repository()));
    assertEquals(0, copies());
  }
  @ParameterizedTest @ValueSource(strings = {"source", "destination"})
  void aCloneWaitingForATransferMustRecheckOwnershipAfterTheLock(String id) throws Exception {
    var pool = Executors.newSingleThreadExecutor();
    var repository = new Repository();
    try (var session = database.session(); var tx = session.beginTransaction()) {
      transfer(tx, id);
      var copy = pool.submit(() -> register(repository));
      assertTrue(repository.entered.await(10, TimeUnit.SECONDS));
      assertThrows(TimeoutException.class, () -> copy.get(200, TimeUnit.MILLISECONDS));
      tx.commit();
      assertNull(copy.get(10, TimeUnit.SECONDS));
      assertEquals(0, copies());
    } finally { pool.shutdownNow(); }
  }
  @Test void unchangedOwnershipRegistersOneCopyWithItsProvenanceAndNewTemplate() {
    var copy = register(new Repository());
    assertNotNull(copy);
    assertEquals("source", copy.getDerivedFrom().getId());
    assertEquals("new-template", ((FolderServerInstance) copy).getIsBasedOn().getId());
    try (var session = database.session()) {
      assertEquals(1, session.run("MATCH (:User {_id:'owner'})-[:OWNS]->(c:Artifact {_id:'clone'}), "
          + "(:Folder {_id:'destination'})-[:CONTAINS]->(c), (c)-[:DERIVEDFROM]->(:Artifact {_id:'source'}) "
          + "RETURN count(c) AS n").single().get("n").asInt());
    }
  }
}
