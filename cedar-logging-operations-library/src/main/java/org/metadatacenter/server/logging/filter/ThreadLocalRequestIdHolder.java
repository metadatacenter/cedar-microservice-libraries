package org.metadatacenter.server.logging.filter;

public class ThreadLocalRequestIdHolder {

  private static final ThreadLocal<LoggingContext> loggingContext = new ThreadLocal<>();

  public static void setLoggingContext(LoggingContext ctx) {
    if (ctx == null) loggingContext.remove();
    else loggingContext.set(ctx);
  }

  public static void clear() {
    loggingContext.remove();
  }

  public static LoggingContext getLoggingContext() {
    return loggingContext.get();
  }

}
