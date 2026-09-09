package org.metadatacenter.proxy;

import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.util.http.ArtifactServiceClient;
import org.metadatacenter.util.http.ProxyUtil;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import java.util.Optional;

public class ArtifactProxy {

  public static Response executeResourceGetByProxyFromArtifactServer(CedarConfig cedarConfig, HttpServletResponse response, CedarResourceType resourceType, String id,
                                                                 Optional<String> format, CedarRequestContext context) throws CedarProcessingException {
    try {
      String url = cedarConfig.getMicroserviceUrlUtil().getArtifact().getArtifactTypeWithId(resourceType, id, format);
      // parameter
      ClassicHttpResponse proxyResponse = new ArtifactServiceClient(cedarConfig).get(url, context);
      if (response != null) {
        ProxyUtil.proxyResponseHeaders(proxyResponse, response);
      }
      HttpEntity entity = proxyResponse.getEntity();
      int statusCode = proxyResponse.getCode();
      String mediaType = entity.getContentType();
      return Response.status(statusCode).type(mediaType).entity(entity.getContent()).build();
    } catch (Exception e) {
      throw new CedarProcessingException(e);
    }
  }
}
