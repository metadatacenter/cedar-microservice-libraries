package org.metadatacenter.server.logging.filter;

import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.UUID;

import static org.metadatacenter.constant.CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY;
import static org.metadatacenter.constant.CedarHeaderParameters.LOCAL_REQUEST_ID_KEY;

@Provider
public class RequestIdGeneratorFilter implements ContainerRequestFilter {

  @Override
  public void filter(ContainerRequestContext requestContext) throws IOException {

    if (requestContext.getMethod() == "OPTIONS") {
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

    ThreadLocalRequestIdHolder.setLoggingContext(new LoggingContext(globalRequestId, localRequestId));

    AppLogger.message(AppLogType.REQUEST_FILTER, AppLogSubType.START, globalRequestId, localRequestId)
        .param(AppLogParam.GLOBAL_REQUEST_ID_SOURCE, requestIdSource)
        .param(AppLogParam.HTTP_METHOD, requestContext.getMethod())
        .param(AppLogParam.PATH, requestContext.getUriInfo().getPath())
        .param(AppLogParam.QUERY_PARAMETERS, requestContext.getUriInfo().getQueryParameters())
        .enqueue();
  }
}
