package org.metadatacenter.server.search.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
import java.nio.charset.StandardCharsets;

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
    ClassicHttpResponse proxyResponse = ProxyUtil.proxyGet(url, requestContext);
    HttpEntity entity = proxyResponse.getEntity();
    if (proxyResponse.getCode() == HttpConstants.OK && entity != null) {
      String artifactString = null;
      JsonNode artifactJson = null;
      try {
        artifactString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        artifactJson = JsonMapper.MAPPER.readTree(artifactString);
      } catch (IOException | ParseException e) {
        throw new CedarProcessingException("Error when reading artifact as Json: " + artifactId);
      }
      return artifactJson;
    } else {
      throw new CedarProcessingException("Error when retrieving artifact: " + artifactId);
    }
  }

}
