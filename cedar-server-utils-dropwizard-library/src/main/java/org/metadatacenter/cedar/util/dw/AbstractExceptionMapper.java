package org.metadatacenter.cedar.util.dw;

import com.mongodb.MongoConnectionPoolClearedException;
import com.mongodb.MongoNodeIsRecoveringException;
import com.mongodb.MongoNotPrimaryException;
import com.mongodb.MongoServerUnavailableException;
import com.mongodb.MongoSocketException;
import com.mongodb.MongoTimeoutException;
import org.metadatacenter.error.CedarErrorPack;
import org.neo4j.driver.exceptions.ServiceUnavailableException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.slf4j.Logger;
import redis.clients.jedis.exceptions.JedisConnectionException;

import java.sql.SQLException;
import java.sql.SQLNonTransientConnectionException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientConnectionException;

public abstract class AbstractExceptionMapper {

  /**
   * Log mapped client outcomes quietly and server failures prominently. A thrown exception is an
   * implementation detail of many ordinary REST outcomes (404, 409 and 412 among them); it does not
   * turn the response into an operational warning. Access logs remain the authoritative record of
   * every response status.
   */
  protected void logMappedException(Logger logger, String marker, Throwable exception, int statusCode,
                                    boolean includeClientStackTrace) {
    if (isServerErrorStatus(statusCode)) {
      logger.error(marker + "full:", exception);
    } else if (includeClientStackTrace) {
      logger.debug(marker + "full:", exception);
    } else {
      logger.debug(marker + "msg :{}", exception.getMessage());
    }
  }

  protected boolean isServerErrorStatus(int statusCode) {
    return statusCode >= 500;
  }

  /**
   * True when Neo4j could not service the request because the graph is unreachable or the session
   * lost its server. Callers sometimes wrap driver exceptions, so inspect the cause chain rather
   * than only the exception Jersey received.
   */
  protected boolean isNeo4jUnavailable(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof ServiceUnavailableException || current instanceof SessionExpiredException) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * True for MongoDB connectivity and failover failures. Query, validation, authentication and
   * application exceptions remain server errors; only failures which can recover when the store or
   * its elected primary returns are classified as dependency outages.
   */
  protected boolean isMongoUnavailable(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof MongoTimeoutException
          || current instanceof MongoSocketException
          || current instanceof MongoServerUnavailableException
          || current instanceof MongoConnectionPoolClearedException
          || current instanceof MongoNodeIsRecoveringException
          || current instanceof MongoNotPrimaryException) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * True for JDBC connection failures, including failures wrapped by Hibernate. SQLState class 08
   * is reserved for connection exceptions; the standard connection exception subclasses also
   * cover pool timeouts whose driver did not supply a SQLState. Query, constraint and transaction
   * errors are deliberately not treated as dependency outages.
   */
  protected boolean isSqlUnavailable(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof SQLTransientConnectionException
          || current instanceof SQLNonTransientConnectionException
          || current instanceof SQLRecoverableException) {
        return true;
      }
      if (current instanceof SQLException sqlException
          && sqlException.getSQLState() != null
          && sqlException.getSQLState().startsWith("08")) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * True when Redis cannot be reached or an established Redis connection is lost. Command and
   * data errors remain server errors; only the Jedis transport exception is a retryable dependency
   * outage.
   */
  protected boolean isRedisUnavailable(Throwable throwable) {
    Throwable current = throwable;
    while (current != null) {
      if (current instanceof JedisConnectionException) {
        return true;
      }
      if (current == current.getCause()) {
        return false;
      }
      current = current.getCause();
    }
    return false;
  }

  /**
   * Return a response copy without the exception object or its stack trace.
   *
   * <p>The original pack is retained for server-side application logging. Query parameters and
   * headers are not an authorization boundary, so a client-controlled debug switch must never make
   * internal class names, downstream URLs, credentials, or stack frames serializable.</p>
   */
  protected CedarErrorPack clientSafeCopy(CedarErrorPack errorPack) {
    CedarErrorPack clientErrorPack = new CedarErrorPack(errorPack);
    clientErrorPack.resetSourceException();
    return clientErrorPack;
  }

}
