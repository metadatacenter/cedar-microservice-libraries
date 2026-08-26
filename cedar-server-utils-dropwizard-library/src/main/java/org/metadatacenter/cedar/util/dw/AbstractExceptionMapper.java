package org.metadatacenter.cedar.util.dw;

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
