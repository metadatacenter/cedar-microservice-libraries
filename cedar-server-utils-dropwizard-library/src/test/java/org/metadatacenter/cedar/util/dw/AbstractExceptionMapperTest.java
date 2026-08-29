package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.mongodb.MongoSocketOpenException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.ServerAddress;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;
import java.util.UUID;

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

  @Test
  void recognizesDirectAndWrappedRedisOutages() {
    assertTrue(mapper.isRedisUnavailable(new JedisConnectionException("connection refused")));
    assertTrue(mapper.isRedisUnavailable(new IllegalStateException("wrapper",
        new JedisConnectionException("connection lost"))));
    assertFalse(mapper.isRedisUnavailable(new IllegalArgumentException("bad command")));
  }

  @Test
  void onlyServerResponsesUseOperationalErrorLogging() {
    assertFalse(mapper.isServerErrorStatus(400));
    assertFalse(mapper.isServerErrorStatus(404));
    assertFalse(mapper.isServerErrorStatus(409));
    assertFalse(mapper.isServerErrorStatus(412));
    assertFalse(mapper.isServerErrorStatus(428));
    assertTrue(mapper.isServerErrorStatus(500));
    assertTrue(mapper.isServerErrorStatus(502));
    assertTrue(mapper.isServerErrorStatus(503));
  }

  @Test
  void mappedClientOutcomesLogAtDebugAndServerFailuresLogAtError() {
    Logger logger = (Logger) org.slf4j.LoggerFactory.getLogger(
        AbstractExceptionMapperTest.class.getName() + "." + UUID.randomUUID());
    logger.setLevel(Level.DEBUG);
    logger.setAdditive(false);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);

    try {
      mapper.logMappedException(logger, ":TEST:", new IllegalArgumentException("missing"), 404, false);
      mapper.logMappedException(logger, ":TEST:", new IllegalStateException("broken"), 500, true);

      assertEquals(2, appender.list.size());
      assertEquals(Level.DEBUG, appender.list.get(0).getLevel());
      assertNull(appender.list.get(0).getThrowableProxy());
      assertEquals(Level.ERROR, appender.list.get(1).getLevel());
      assertNotNull(appender.list.get(1).getThrowableProxy());
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }
}
