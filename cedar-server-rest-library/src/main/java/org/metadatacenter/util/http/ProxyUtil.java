package org.metadatacenter.util.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.Lists;
import org.apache.commons.codec.CharEncoding;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConnectionConstants;
import org.metadatacenter.exception.CedarBadRequestException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.util.json.JsonMapper;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.util.List;

public class ProxyUtil {

  public static final String ZERO_LENGTH = "0";

  private static final List<String> CEDAR_RESPONSE_HEADERS = Lists.newArrayList(
      HttpHeaders.CONTENT_TYPE,
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS,
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_REPORT,
      CustomHttpConstants.HEADER_ACCESS_CONTROL_EXPOSE_HEADERS);

  public static HttpResponse proxyGet(String url, CedarRequestContext context) throws CedarProcessingException {
    Request proxyRequest = Request.Get(url)
        .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
        .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT);
    copyHeaders(proxyRequest, context);
    try {
      return proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public static HttpResponse proxyDelete(String url, CedarRequestContext context) throws CedarProcessingException {
    Request proxyRequest = Request.Delete(url)
        .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
        .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
        .addHeader(HttpHeaders.CONTENT_LENGTH, ZERO_LENGTH)
        .addHeader(HttpHeaders.CONTENT_TYPE, ContentType.TEXT_PLAIN.toString());
    copyHeaders(proxyRequest, context);
    try {
      return proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public static HttpResponse proxyPost(String url, CedarRequestContext context) throws CedarProcessingException,
      CedarBadRequestException {
    return proxyPost(url, context, context.request().getRequestBody().asJsonString());
  }

  public static HttpResponse proxyPost(String url, CedarRequestContext context, String content) throws
      CedarProcessingException {
    Request proxyRequest = Request.Post(url)
        .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
        .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
        .bodyString(content, ContentType.APPLICATION_JSON);
    copyHeaders(proxyRequest, context);
    try {
      return proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public static HttpResponse proxyPut(String url, CedarRequestContext context) throws CedarProcessingException,
      CedarBadRequestException {
    return proxyPut(url, context, context.request().getRequestBody().asJsonString());
  }

  public static HttpResponse proxyPut(String url, CedarRequestContext context, String content) throws
      CedarProcessingException {
    Request proxyRequest = Request.Put(url)
        .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
        .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
        .bodyString(content, ContentType.APPLICATION_JSON);
    copyHeaders(proxyRequest, context);
    try {
      return proxyRequest.execute().returnResponse();
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }

  public static void proxyResponseHeaders(HttpResponse proxyResponse, HttpServletResponse response) {
    for (Header header : proxyResponse.getAllHeaders()) {
      if (CEDAR_RESPONSE_HEADERS.contains(header.getName())) {
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

  private static void copyHeader(Request proxyRequest, String headerKey, String value) {
    if (value != null) {
      proxyRequest.setHeader(headerKey, value);
    }
  }

  public static JsonNode proxyGetBodyAsJsonNode(String url, CedarRequestContext context) throws CedarProcessingException {
    HttpResponse proxyResponse = ProxyUtil.proxyGet(url, context);
    HttpEntity proxyEntity = proxyResponse.getEntity();
    try {
      String proxyString = EntityUtils.toString(proxyEntity, CharEncoding.UTF_8);
      return JsonMapper.MAPPER.readTree(proxyString);
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
  }
}
