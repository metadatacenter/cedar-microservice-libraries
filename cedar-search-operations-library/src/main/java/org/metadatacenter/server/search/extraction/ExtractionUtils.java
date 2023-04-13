package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang.CharEncoding;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.util.http.CedarUrlUtil;
import org.metadatacenter.util.http.ProxyUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Utilities used to extract information from CEDAR artifacts
 */
public class ExtractionUtils {

  private static final Logger log = LoggerFactory.getLogger(ExtractionUtils.class);

  private final CedarConfig cedarConfig;

  public ExtractionUtils(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public JsonNode getArtifactById(String artifactId, CedarResourceType nodeType,
                                  CedarRequestContext requestContext) throws CedarProcessingException {
    String url =
        cedarConfig.getMicroserviceUrlUtil().getArtifact().getResourceType(nodeType) + "/"
            + CedarUrlUtil.urlEncode(artifactId);
    HttpResponse proxyResponse = ProxyUtil.proxyGet(url, requestContext);
    HttpEntity entity = proxyResponse.getEntity();
    if (proxyResponse.getStatusLine().getStatusCode() == HttpConstants.OK && entity != null) {
      String artifactString = null;
      JsonNode artifactJson = null;
      try {
        artifactString = EntityUtils.toString(entity, CharEncoding.UTF_8);
        artifactJson = JsonMapper.MAPPER.readTree(artifactString);
      } catch (IOException e) {
        throw new CedarProcessingException("Error when reading artifact as Json: " + artifactId);
      }
      return artifactJson;
    } else {
      throw new CedarProcessingException("Error when retrieving artifact: " + artifactId);
    }
  }

}
