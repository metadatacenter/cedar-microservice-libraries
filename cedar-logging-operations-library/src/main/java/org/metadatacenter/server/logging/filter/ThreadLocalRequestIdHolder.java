package org.metadatacenter.server.logging.filter;

public class ThreadLocalRequestIdHolder {

  private static final ThreadLocal<LoggingContext> loggingContext = new ThreadLocal<>();

  public static void setLoggingContext(LoggingContext ctx) {
    loggingContext.set(ctx);
  }

  public static LoggingContext getLoggingContext() {
    return loggingContext.get();
  }

}
