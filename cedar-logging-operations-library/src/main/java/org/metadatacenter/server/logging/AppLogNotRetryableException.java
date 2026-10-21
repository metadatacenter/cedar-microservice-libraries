package org.metadatacenter.server.logging;

/**
 * The message itself can never be written, so handling it again cannot succeed. The queue consumer moves it to the
 * dead-letter queue at once instead of retrying it: a retry only costs time while the live logs queue behind it.
 */
public class AppLogNotRetryableException extends RuntimeException {

  public AppLogNotRetryableException(String message, Throwable cause) {
    super(message, cause);
  }
}
