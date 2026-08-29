package org.metadatacenter.util.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.apache.commons.codec.CharEncoding;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConnectionConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.exception.CedarBadRequestException;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class ProxyUtil {

  public static final String ZERO_LENGTH = "0";

  private static final List<String> CEDAR_RESPONSE_HEADERS = Lists.newArrayList(
      HttpHeaders.CONTENT_TYPE,
      HttpHeaders.ETAG,
      HttpHeaders.VARY,
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS,
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_REPORT,
      HttpConstants.HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS);

  public static ClassicHttpResponse proxyGet(String url, CedarRequestContext context) throws CedarProcessingException {
    Request proxyRequest = Request.get(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT));
    copyHeaders(proxyRequest, context);
    requestIdentityEncoding(proxyRequest);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyGet(String url, CedarRequestContext context, Map<String, String> additionalHeaders) throws CedarProcessingException {
    Request proxyRequest = Request.get(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT));
    copyHeaders(proxyRequest, context);
    copyHeaders(proxyRequest, additionalHeaders);
    requestIdentityEncoding(proxyRequest);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyGet(String url, Map<String, String> additionalHeaders) throws CedarProcessingException {
    Request proxyRequest = Request.get(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT));
    copyHeaders(proxyRequest, additionalHeaders);
    requestIdentityEncoding(proxyRequest);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyDelete(String url, CedarRequestContext context) throws CedarProcessingException {
    return proxyDelete(url, context, context.getIfMatchHeader());
  }

  public static ClassicHttpResponse proxyDelete(String url, CedarRequestContext context, String ifMatch)
      throws CedarProcessingException {
    // HttpClient 5 sets Content-Length itself from the (empty) body; adding it explicitly, as the
    // HttpClient 4 code did, makes the client reject the request with "Content-Length header
    // already present". Only the content type is set here.
    Request proxyRequest = Request.delete(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT))
        .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
    copyHeaders(proxyRequest, context);
    copyHeader(proxyRequest, HttpHeaders.IF_MATCH, ifMatch);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyPost(String url, CedarRequestContext context) throws CedarProcessingException,
      CedarBadRequestException {
    return proxyPost(url, context, context.request().getRequestBody().asJsonString());
  }

  public static ClassicHttpResponse proxyPost(String url, CedarRequestContext context, String content) throws CedarProcessingException {
    Request proxyRequest = Request.post(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT))
        .bodyString(content, ContentType.APPLICATION_JSON);
    copyHeaders(proxyRequest, context);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyPost(String url, Map<String, String> additionalHeaders, String content) throws CedarProcessingException {
    Request proxyRequest = Request.post(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT))
        .bodyString(content, ContentType.APPLICATION_FORM_URLENCODED);
    copyHeaders(proxyRequest, additionalHeaders);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  public static ClassicHttpResponse proxyPut(String url, CedarRequestContext context) throws CedarProcessingException,
      CedarBadRequestException {
    return proxyPut(url, context, context.request().getRequestBody().asJsonString());
  }

  public static ClassicHttpResponse proxyPut(String url, CedarRequestContext context, String content) throws CedarProcessingException {
    return proxyPut(url, context, content, context.getIfMatchHeader());
  }

  public static ClassicHttpResponse proxyPut(String url, CedarRequestContext context, String content, String ifMatch)
      throws CedarProcessingException {
    Request proxyRequest = Request.put(url)
        .connectTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.CONNECTION_TIMEOUT))
        .responseTimeout(Timeout.ofMilliseconds(HttpConnectionConstants.SOCKET_TIMEOUT))
        .bodyString(content, ContentType.APPLICATION_JSON);
    copyHeaders(proxyRequest, context);
    copyHeader(proxyRequest, HttpHeaders.IF_MATCH, ifMatch);
    try {
      return (ClassicHttpResponse) proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw dependencyUnavailable(e);
    }
  }

  private static CedarDependencyUnavailableException dependencyUnavailable(IOException cause) {
    // Do not put the URL in the client-facing message: several callers carry identifiers or API
    // credentials in their downstream path or query string. The cause remains available in the
    // server log under the request's correlation id.
    return new CedarDependencyUnavailableException("Downstream service is unavailable", cause);
  }

  public static void proxyResponseHeaders(ClassicHttpResponse proxyResponse, HttpServletResponse response) {
    for (Header header : proxyResponse.getHeaders()) {
      if (CEDAR_RESPONSE_HEADERS.stream().anyMatch(name -> name.equalsIgnoreCase(header.getName()))) {
        response.setHeader(header.getName(), header.getValue());
      }
    }
  }

  private static void copyHeaders(Request proxyRequest, CedarRequestContext context) {
    copyHeader(proxyRequest, HttpHeaders.AUTHORIZATION, context.getAuthorizationHeader());
    copyHeader(proxyRequest, CedarHeaderParameters.DEBUG, context.getDebugHeader());
    copyHeader(proxyRequest, CedarHeaderParameters.CLIENT_SESSION_ID, context.getClientSessionIdHeader());
    copyHeader(proxyRequest, CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY, context.getGlobalRequestIdHeader());
    copyHeader(proxyRequest, CedarHeaderParameters.LOCAL_REQUEST_ID_KEY, context.getLocalRequestIdHeader());
  }

  private static void copyHeaders(Request proxyRequest, Map<String, String> additionalHeader) {
    for (String key : additionalHeader.keySet()) {
      proxyRequest.addHeader(key, additionalHeader.get(key));
    }
  }

  private static void copyHeader(Request proxyRequest, String headerKey, String value) {
    if (value != null) {
      proxyRequest.setHeader(headerKey, value);
    }
  }

  private static void requestIdentityEncoding(Request proxyRequest) {
    // These calls are application-to-application proxies. Let the public response layer choose its
    // own content coding once; otherwise an upstream gzip variant's ETag is forwarded and Jetty
    // appends a second -gzip marker when it compresses the public response.
    proxyRequest.setHeader(HttpHeaders.ACCEPT_ENCODING, "identity");
  }

  public static JsonNode proxyGetBodyAsJsonNode(String url, CedarRequestContext context) throws CedarProcessingException {
    ClassicHttpResponse proxyResponse = ProxyUtil.proxyGet(url, context);
    HttpEntity proxyEntity = proxyResponse.getEntity();
    try {
      String proxyString = EntityUtils.toString(proxyEntity, CharEncoding.UTF_8);
      return JsonMapper.MAPPER.readTree(proxyString);
    } catch (IOException | ParseException e) {
      throw new CedarProcessingException(e);
    }
  }
}
