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

public abstract class AbstractExceptionMapper {

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
