package org.metadatacenter.server.logging.filter;

import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;

import static org.metadatacenter.constant.CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY;
import static org.metadatacenter.constant.CedarHeaderParameters.LOCAL_REQUEST_ID_KEY;

@Provider
public class ResponseLoggerFilter implements ContainerResponseFilter {

  @Override
  public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext)
      throws IOException {
    String globalRequestId = requestContext.getHeaderString(GLOBAL_REQUEST_ID_KEY);
    String localRequestId = requestContext.getHeaderString(LOCAL_REQUEST_ID_KEY);
    // The HTTP status is only available here, at response time (post exception-mapping), so it is
    // carried on the END event and merged into the request row by ApplicationRequestLog.mergeEndLog.
    AppLogger.message(AppLogType.REQUEST_FILTER, AppLogSubType.END, globalRequestId, localRequestId)
        .param(AppLogParam.STATUS, responseContext.getStatus())
        .enqueue();
  }
}
