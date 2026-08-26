package org.metadatacenter.cedar.util.dw;

import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractExceptionMapperTest {

  private final AbstractExceptionMapper mapper = new AbstractExceptionMapper() {
  };

  @Test
  void clientCopyKeepsTheContractButRemovesInternalExceptionDetails() {
    CedarErrorPack original = new CedarErrorPack()
        .status(CedarResponseStatus.SERVICE_UNAVAILABLE)
        .message("Downstream service is unavailable")
        .sourceException(new IOException("Connect to http://secret-host:1234 failed"));

    CedarErrorPack client = mapper.clientSafeCopy(original);

    assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, client.getStatus());
    assertEquals("Downstream service is unavailable", client.getMessage());
    assertNull(client.getOriginalException());
    assertNull(client.getSourceException());

    assertNotNull(original.getOriginalException(), "sanitizing the response must not erase log detail");
    assertNotNull(original.getSourceException(), "sanitizing the response must not erase log detail");
  }

  @Test
  void recognizesDirectAndWrappedNeo4jOutages() {
    assertTrue(mapper.isNeo4jUnavailable(new ServiceUnavailableException("down")));
    assertTrue(mapper.isNeo4jUnavailable(
        new IllegalStateException("wrapper", new SessionExpiredException("lost"))));
    assertFalse(mapper.isNeo4jUnavailable(new IllegalStateException("application defect")));
  }

  @Test
  void recognizesDirectAndWrappedMongoOutages() {
    assertTrue(mapper.isMongoUnavailable(new MongoTimeoutException("selection timed out")));
    assertTrue(mapper.isMongoUnavailable(new IllegalStateException("wrapper",
        new MongoSocketOpenException("connect failed", new ServerAddress("127.0.0.1", 1),
            new IOException("refused")))));
    assertFalse(mapper.isMongoUnavailable(new IllegalArgumentException("bad query")));
  }

  @Test
  void recognizesOnlySqlConnectionFailuresAsOutages() {
    assertTrue(mapper.isSqlUnavailable(new SQLTransientConnectionException("pool timed out")));
    assertTrue(mapper.isSqlUnavailable(
        new IllegalStateException("Hibernate wrapper", new SQLRecoverableException("connection lost"))));
    assertTrue(mapper.isSqlUnavailable(new SQLException("communications failure", "08S01")));
    assertFalse(mapper.isSqlUnavailable(new SQLException("unique constraint", "23000")));
    assertFalse(mapper.isSqlUnavailable(new IllegalArgumentException("bad query")));
  }
}
