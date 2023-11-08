package org.metadatacenter.proxy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.util.http.ProxyUtil;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Response;
import java.util.Optional;

public class ArtifactProxy {

  public static Response executeResourceGetByProxyFromArtifactServer(MicroserviceUrlUtil microserviceUrlUtil, HttpServletResponse response, CedarResourceType resourceType, String id,
                                                                 Optional<String> format, CedarRequestContext context) throws CedarProcessingException {
    try {
      String url = microserviceUrlUtil.getArtifact().getArtifactTypeWithId(resourceType, id, format);
      // parameter
      HttpResponse proxyResponse = ProxyUtil.proxyGet(url, context);
      if (response != null) {
        ProxyUtil.proxyResponseHeaders(proxyResponse, response);
      }
      HttpEntity entity = proxyResponse.getEntity();
      int statusCode = proxyResponse.getStatusLine().getStatusCode();
      String mediaType = entity.getContentType().getValue();
      return Response.status(statusCode).type(mediaType).entity(entity.getContent()).build();
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }
}
