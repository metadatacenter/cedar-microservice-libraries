package org.metadatacenter.util.http;

import jakarta.ws.rs.core.HttpHeaders;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.metadatacenter.config.ArtifactServiceConfig;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.io.IOException;
import java.net.URI;
import java.util.Objects;

/** Authenticated internal calls to the configured artifact service, preserving the user identity. */
public final class ArtifactServiceClient {
  private final URI base;
  private final String key;

  public ArtifactServiceClient(CedarConfig config) {
    base = URI.create(config.getServers().getArtifact().getBase());
    key = config.getArtifactService().requireApiKey();
  }

  public ClassicHttpResponse get(String url, CedarRequestContext context) throws CedarProcessingException {
    return get(url, context, HttpTimeouts.INTERACTIVE);
  }

  public ClassicHttpResponse get(String url, CedarRequestContext context, HttpTimeouts timeouts)
      throws CedarProcessingException {
    return execute(url, Request.get(url).setHeader(HttpHeaders.ACCEPT_ENCODING, "identity"), context, timeouts);
  }

  public ClassicHttpResponse post(String url, CedarRequestContext context, String content)
      throws CedarProcessingException {
    return post(url, context, content, HttpTimeouts.INTERACTIVE);
  }

  public ClassicHttpResponse post(String url, CedarRequestContext context, String content, HttpTimeouts timeouts)
      throws CedarProcessingException {
    return execute(url, Request.post(url).bodyString(content, ContentType.APPLICATION_JSON), context, timeouts);
  }

  public ClassicHttpResponse put(String url, CedarRequestContext context, String content)
      throws CedarProcessingException {
    return put(url, context, content, context.getIfMatchHeader());
  }

  public ClassicHttpResponse put(String url, CedarRequestContext context, String content, String ifMatch)
      throws CedarProcessingException {
    Request request = Request.put(url).bodyString(content, ContentType.APPLICATION_JSON);
    header(request, HttpHeaders.IF_MATCH, ifMatch);
    return execute(url, request, context, HttpTimeouts.INTERACTIVE);
  }

  public ClassicHttpResponse delete(String url, CedarRequestContext context, String ifMatch)
      throws CedarProcessingException {
    Request request = Request.delete(url);
    header(request, HttpHeaders.IF_MATCH, ifMatch);
    return execute(url, request, context, HttpTimeouts.INTERACTIVE);
  }

  private ClassicHttpResponse execute(String url, Request request, CedarRequestContext context, HttpTimeouts timeouts)
      throws CedarProcessingException {
    URI target = URI.create(url);
    if (!Objects.equals(base.getScheme(), target.getScheme()) || !Objects.equals(base.getHost(), target.getHost())
        || base.getPort() != target.getPort() || target.getUserInfo() != null || target.getFragment() != null
        || target.getHost() == null || !("http".equals(target.getScheme()) || "https".equals(target.getScheme()))
        || !target.normalize().getRawPath().startsWith(base.getRawPath())) {
      throw new IllegalArgumentException("Artifact service credentials can only be sent to the configured artifact service");
    }
    // Never copy an incoming service-key header. Only installation configuration can supply it.
    header(request, ArtifactServiceConfig.HEADER, key);
    header(request, HttpHeaders.AUTHORIZATION, context.getAuthorizationHeader());
    header(request, CedarHeaderParameters.DEBUG, context.getDebugHeader());
    header(request, CedarHeaderParameters.CLIENT_SESSION_ID, context.getClientSessionIdHeader());
    header(request, CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY, context.getGlobalRequestIdHeader());
    header(request, CedarHeaderParameters.LOCAL_REQUEST_ID_KEY, context.getLocalRequestIdHeader());
    try {
      return (timeouts == HttpTimeouts.BATCH ? HttpTimeouts.ARTIFACT_BATCH : HttpTimeouts.ARTIFACT_INTERACTIVE)
          .execute(request);
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("Downstream service is unavailable", e);
    }
  }

  private static void header(Request request, String name, String value) {
    if (value != null) request.setHeader(name, value);
  }
}
