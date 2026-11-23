package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.GraphTransactionTimeouts;
import org.metadatacenter.server.neo4j.ScratchNeo4j;
import org.mockito.ArgumentCaptor;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.Transaction;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.TransactionWork;
import org.neo4j.driver.exceptions.ClientException;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Every graph query the proxies run carries a time limit, and Neo4j enforces it. */
class GraphTransactionTimeoutTest {

  private Session session;
  private Probe probe;

  /** Exposes the helpers every proxy uses. */
  static class Probe extends AbstractNeo4JProxy {
    Probe(Neo4JProxies proxies) {
      super(proxies, mock(CedarConfig.class));
    }
  }

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() throws ReflectiveOperationException {
    Transaction tx = mock(Transaction.class);
    Result empty = mock(Result.class);
    when(empty.hasNext()).thenReturn(false);
    when(tx.run(anyString())).thenReturn(empty);
    when(tx.run(anyString(), any(Map.class))).thenReturn(empty);

    session = mock(Session.class);
    when(session.writeTransaction(any(TransactionWork.class), any(TransactionConfig.class)))
        .thenAnswer(call -> call.<TransactionWork<Object>>getArgument(0).execute(tx));
    when(session.readTransaction(any(TransactionWork.class), any(TransactionConfig.class)))
        .thenAnswer(call -> call.<TransactionWork<Object>>getArgument(0).execute(tx));

    Driver driver = mock(Driver.class);
    when(driver.session()).thenReturn(session);
    Neo4JProxies proxies = mock(Neo4JProxies.class);
    Field field = Neo4JProxies.class.getDeclaredField("driver");
    field.setAccessible(true);
    field.set(proxies, driver);
    probe = new Probe(proxies);
  }

  @Test
  void theRequestLimitIsThirtySeconds() {
    assertEquals(Duration.ofSeconds(30), GraphTransactionTimeouts.request().timeout());
    assertTrue(GraphTransactionTimeouts.longRunning().timeout().compareTo(Duration.ofSeconds(49)) > 0,
        "the permission recalculation took 49 seconds in production");
  }

  @Test
  @SuppressWarnings("unchecked")
  void everyHelperOpensItsTransactionWithTheRequestLimit() {
    CypherQuery q = new CypherQueryLiteral("RETURN 1");
    probe.executeWrite(q, "writing");
    probe.executeWriteBatch(List.of(q, q), "writing a batch");
    probe.executeWriteGetOne(q, FileSystemResource.class);
    probe.executeReadGetOne(q, FileSystemResource.class);
    probe.executeReadGetList(q, FileSystemResource.class);
    probe.executeReadGetIdList(q, org.metadatacenter.id.CedarFolderId.class);
    probe.executeReadGetStringList(q);
    probe.executeReadGetArcList(q);
    probe.executeReadGetMapList(q, List.of("name"));
    probe.executeInWriteTransaction(tx -> null, "working");
    probe.executeInReadTransaction(tx -> null, "reading");

    ArgumentCaptor<TransactionConfig> writes = ArgumentCaptor.forClass(TransactionConfig.class);
    ArgumentCaptor<TransactionConfig> reads = ArgumentCaptor.forClass(TransactionConfig.class);
    verify(session, atLeastOnce()).writeTransaction(any(TransactionWork.class), writes.capture());
    verify(session, atLeastOnce()).readTransaction(any(TransactionWork.class), reads.capture());
    assertEquals(4, writes.getAllValues().size());
    assertEquals(7, reads.getAllValues().size());
    writes.getAllValues().forEach(c -> assertEquals(GraphTransactionTimeouts.request(), c));
    reads.getAllValues().forEach(c -> assertEquals(GraphTransactionTimeouts.request(), c));
  }

  @Test
  @SuppressWarnings("unchecked")
  void aBackgroundReadCanTakeTheLongerLimit() {
    probe.executeReadGetList(new CypherQueryLiteral("RETURN 1"), FileSystemResource.class,
        GraphTransactionTimeouts.longRunning());

    verify(session).readTransaction(any(TransactionWork.class), org.mockito.ArgumentMatchers.eq(
        GraphTransactionTimeouts.longRunning()));
  }

  @Test
  @Tag("integration")
  void neo4jEndsATransactionThatOutlivesItsLimit() {
    try (Driver driver = GraphDatabase.driver(ScratchNeo4j.boltURI(), AuthTokens.none());
         Session real = driver.session()) {
      // The in-process server ends this query only once it has counted every row. A Neo4j 5.26.32
      // server ended the same query, and a variable-length expansion, within 1.2 seconds of a 300 ms
      // limit. This test therefore checks only that the limit reaches the server and how it reports.
      TransactionConfig brief = TransactionConfig.builder().withTimeout(Duration.ofMillis(200)).build();
      ClientException ended = assertThrows(ClientException.class, () -> real.readTransaction(
          tx -> tx.run("UNWIND range(1, 200000000) AS x RETURN count(x) AS n").single().get("n").asLong(), brief));
      assertTrue(GraphTransactionTimeouts.isTimeout(ended.code()), "unexpected code " + ended.code());
    }
  }
}
