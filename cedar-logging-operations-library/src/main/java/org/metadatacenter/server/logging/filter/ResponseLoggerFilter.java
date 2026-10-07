package org.metadatacenter.server.logging.filter;

import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.annotation.Priority;
import java.io.IOException;


@Provider
@Priority(Integer.MIN_VALUE)
public class ResponseLoggerFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    try {
      // Use the context that produced START, even if another filter changed the request headers.
      // OPTIONS has no START and must not leave an orphan END in the application log.
      Object value = requestContext.getProperty(RequestIdGeneratorFilter.CONTEXT_PROPERTY);
      requestContext.removeProperty(RequestIdGeneratorFilter.CONTEXT_PROPERTY);
      if (value instanceof LoggingContext context) {
        // Response filters run in reverse priority order: this sees the status after other filters.
        AppLogger.message(AppLogType.REQUEST_FILTER, AppLogSubType.END,
                context.getGlobalRequestId(), context.getLocalRequestId())
            .param(AppLogParam.STATUS, responseContext.getStatus())
            .enqueue();
      }
    } finally {
      ThreadLocalRequestIdHolder.clear();
    }
  }
}
