package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.error.CedarErrorPack;

public abstract class AbstractExceptionMapper {

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
