package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AbstractNeo4JProxyTest {

  @Test
  void healthyConnectionEstablishmentHasHeadroomBeyondTheFastOutageTimeout() {
    assertEquals(5_000, AbstractNeo4JProxy.testConnectionTimeoutMillis(1_000));
  }

  @Test
  void aLargerConfiguredTimeoutIsPreserved() {
    assertEquals(10_000, AbstractNeo4JProxy.testConnectionTimeoutMillis(10_000));
  }
}
