package org.metadatacenter.cedar.util.dw;

import org.apache.commons.codec.digest.DigestUtils;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.HttpServletRequestContext;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;
import org.metadatacenter.server.security.model.user.CedarUserAuthSource;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.util.provenance.ProvenanceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.UriInfo;

import static org.metadatacenter.constant.HttpConstants.HTTP_AUTH_HEADER_BEARER_PREFIX;

@Produces(MediaType.APPLICATION_JSON)
public abstract class CedarMicroserviceResource {

  protected
  @Context
  UriInfo uriInfo;

  protected
  @Context
  HttpServletRequest request;

  protected
  @Context
  HttpServletResponse response;

  protected
  @Context
  HttpHeaders httpHeaders;

  private static final Logger log = LoggerFactory.getLogger(CedarMicroserviceResource.class);

  protected final CedarConfig cedarConfig;
  protected final LinkedDataUtil linkedDataUtil;
  protected final MicroserviceUrlUtil microserviceUrlUtil;
  protected final ProvenanceUtil provenanceUtil;

  protected CedarMicroserviceResource(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    linkedDataUtil = cedarConfig.getLinkedDataUtil();
    microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
    provenanceUtil = new ProvenanceUtil();
  }

  protected CedarRequestContext buildRequestContext() throws CedarAccessException {
    HttpServletRequestContext sc = new HttpServletRequestContext(linkedDataUtil, request, httpHeaders);
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StackTraceElement caller = stackTrace[2];

    String authHeader = sc.getAuthorizationHeader();
    String jwtTokenHash = null;
    if (authHeader != null && (authHeader.regionMatches(true, 0, HTTP_AUTH_HEADER_BEARER_PREFIX, 0, HTTP_AUTH_HEADER_BEARER_PREFIX.length()))) {
      String headerValue = authHeader.substring(HTTP_AUTH_HEADER_BEARER_PREFIX.length());
      jwtTokenHash = DigestUtils.md5Hex(headerValue);
    }

    AppLogger.message(AppLogType.REQUEST_HANDLER, AppLogSubType.START, sc.getGlobalRequestIdHeader(),
        sc.getLocalRequestIdHeader())
        .param(AppLogParam.CLASS_NAME, caller.getClassName())
        .param(AppLogParam.METHOD_NAME, caller.getMethodName())
        .param(AppLogParam.LINE_NUMBER, caller.getLineNumber())
        .param(AppLogParam.USER_ID, sc.getCedarUser() != null ? sc.getCedarUser().getId() : null)
        .param(AppLogParam.CLIENT_SESSION_ID, sc.getClientSessionIdHeader())
        .param(AppLogParam.JWT_TOKEN_HASH, jwtTokenHash)
        .param(AppLogParam.AUTH_SOURCE, sc.getCedarUser() != null ? sc.getCedarUser().getAuthSource() : null)
        .enqueue();

    if (sc.getUserCreationException() != null) {
      throw sc.getUserCreationException();
    }
    return sc;
  }

  protected CedarRequestContext buildAnonymousRequestContext() {
    HttpServletRequestContext sc = new HttpServletRequestContext(linkedDataUtil, request, httpHeaders);
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StackTraceElement caller = stackTrace[2];

    AppLogger.message(AppLogType.REQUEST_HANDLER, AppLogSubType.START, sc.getGlobalRequestIdHeader(),
        sc.getLocalRequestIdHeader())
        .param(AppLogParam.CLASS_NAME, caller.getClassName())
        .param(AppLogParam.METHOD_NAME, caller.getMethodName())
        .param(AppLogParam.LINE_NUMBER, caller.getLineNumber())
        .param(AppLogParam.CLIENT_SESSION_ID, sc.getClientSessionIdHeader())
        .param(AppLogParam.AUTH_SOURCE, CedarUserAuthSource.ANONYMOUS)
        .enqueue();

    return sc;
  }


}
