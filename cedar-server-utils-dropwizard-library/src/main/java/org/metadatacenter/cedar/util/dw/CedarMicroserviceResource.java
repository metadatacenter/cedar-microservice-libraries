package org.metadatacenter.cedar.util.dw;

import org.apache.commons.codec.digest.DigestUtils;
import org.metadatacenter.bridge.CedarDataServices;
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

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;

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

  /**
   * The workspace and graph services, received as a field rather than reached as a global from each
   * method.
   *
   * <p>Five servers declared this seam for themselves, each with the same field and the same pair of
   * constructors. It belongs to every resource that reaches the graph, so it is here: the
   * one-argument constructor supplies the single managed instance from the sanctioned
   * composition-root accessor, and the two-argument one lets a test inject a specific one.
   */
  protected final CedarDataServices dataServices;

  protected CedarMicroserviceResource(CedarConfig cedarConfig) {
    this(cedarConfig, CedarDataServices.getInstance());
  }

  protected CedarMicroserviceResource(CedarConfig cedarConfig, CedarDataServices dataServices) {
    this.cedarConfig = cedarConfig;
    this.dataServices = dataServices;
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

  /**
   * Builds a request context for a caller holding no credential.
   *
   * <p>Serves only methods annotated {@link AnonymousAccess}, so a method reaches this path because someone
   * declared that it should rather than because someone typed this helper instead of
   * {@link #buildRequestContext()}. A method that calls this without the annotation is a wiring error, and
   * fails here rather than answering an unauthenticated caller.
   */
  protected CedarRequestContext buildAnonymousRequestContext() {
    HttpServletRequestContext sc = new HttpServletRequestContext(linkedDataUtil, request, httpHeaders);
    StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
    StackTraceElement caller = stackTrace[2];
    requireDeclaredAnonymous(caller);

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

  private static final Map<String, Boolean> ANONYMOUS_DECLARATIONS = new ConcurrentHashMap<>();

  /**
   * Fails unless the method that asked for an anonymous context is annotated {@link AnonymousAccess}.
   *
   * <p>Every method declared under the caller's name must carry the annotation, so an overload cannot inherit
   * the exemption from a sibling. The answer is cached: the classes and their annotations do not change while
   * the server runs.
   */
  private static void requireDeclaredAnonymous(StackTraceElement caller) {
    String key = caller.getClassName() + "#" + caller.getMethodName();
    boolean declared = ANONYMOUS_DECLARATIONS.computeIfAbsent(key, k -> isDeclaredAnonymous(caller));
    if (!declared) {
      throw new IllegalStateException(
          "An anonymous request context was requested by " + key + ", which is not annotated @AnonymousAccess. "
              + "Annotate the resource method if it is meant to serve a caller holding no credential, or call "
              + "buildRequestContext() instead.");
    }
  }

  private static boolean isDeclaredAnonymous(StackTraceElement caller) {
    try {
      Class<?> callerClass = Class.forName(caller.getClassName());
      List<Method> named = Arrays.stream(callerClass.getDeclaredMethods())
          .filter(m -> m.getName().equals(caller.getMethodName()))
          .toList();
      return !named.isEmpty() && named.stream().allMatch(m -> m.isAnnotationPresent(AnonymousAccess.class));
    } catch (ClassNotFoundException e) {
      log.error("Could not resolve {} while checking for @AnonymousAccess", caller.getClassName(), e);
      return false;
    }
  }
}
