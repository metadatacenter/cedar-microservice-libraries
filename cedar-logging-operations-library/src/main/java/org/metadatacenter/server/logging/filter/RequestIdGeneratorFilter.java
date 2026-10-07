package org.metadatacenter.server.logging.filter;

import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.container.PreMatching;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import java.io.IOException;
import java.util.UUID;

import static org.metadatacenter.constant.CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY;
import static org.metadatacenter.constant.CedarHeaderParameters.LOCAL_REQUEST_ID_KEY;

@Provider
@PreMatching
@Priority(Priorities.AUTHENTICATION - 100)
public class RequestIdGeneratorFilter implements ContainerRequestFilter {
  static final String CONTEXT_PROPERTY = RequestIdGeneratorFilter.class.getName() + ".context";

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {

    ThreadLocalRequestIdHolder.clear();
    requestContext.removeProperty(CONTEXT_PROPERTY);
    if ("OPTIONS".equals(requestContext.getMethod())) {
      return;
    }

    String globalRequestId = requestContext.getHeaderString(GLOBAL_REQUEST_ID_KEY);
    String requestIdSource = "new";
    if (globalRequestId == null) {
      globalRequestId = UUID.randomUUID().toString();
      requestContext.getHeaders().remove(GLOBAL_REQUEST_ID_KEY);
      requestContext.getHeaders().add(GLOBAL_REQUEST_ID_KEY, globalRequestId);
    } else {
      requestIdSource = "request";
    }
    String localRequestId = UUID.randomUUID().toString();
    requestContext.getHeaders().remove(LOCAL_REQUEST_ID_KEY);
    requestContext.getHeaders().add(LOCAL_REQUEST_ID_KEY, localRequestId);

    LoggingContext context = new LoggingContext(globalRequestId, localRequestId);
    ThreadLocalRequestIdHolder.setLoggingContext(context);

    AppLogger.message(AppLogType.REQUEST_FILTER, AppLogSubType.START, globalRequestId, localRequestId)
        .param(AppLogParam.GLOBAL_REQUEST_ID_SOURCE, requestIdSource)
        .param(AppLogParam.HTTP_METHOD, requestContext.getMethod())
        .param(AppLogParam.PATH, requestContext.getUriInfo().getPath())
        .param(AppLogParam.QUERY_PARAMETERS, requestContext.getUriInfo().getQueryParameters())
        .enqueue();
    requestContext.setProperty(CONTEXT_PROPERTY, context);
  }
}
