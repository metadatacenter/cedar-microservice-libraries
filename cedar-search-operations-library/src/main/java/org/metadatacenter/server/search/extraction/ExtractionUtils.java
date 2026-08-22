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
import java.util.Optional;

/**
 * Utilities used to extract information from CEDAR artifacts
 */
public class ExtractionUtils {

  private static final Logger log = LoggerFactory.getLogger(ExtractionUtils.class);

  private final CedarConfig cedarConfig;

  public ExtractionUtils(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  /**
   * Retrieves an artifact's content from the artifact server.
   * <p>
   * An artifact that is not there and an artifact server that cannot be reached are different
   * outcomes and are reported differently. An absent artifact is a normal state — the graph and the
   * artifact server disagree, most often because a deletion is in flight — so it comes back as an
   * empty result for the caller to handle. Anything else is a failure of the retrieval itself and
   * is thrown, carrying the status code so the cause is visible rather than guessed at.
   *
   * @return the artifact's content, or empty when the artifact server does not have it
   */
  public Optional<JsonNode> getArtifactById(String artifactId, CedarResourceType nodeType,
                                            CedarRequestContext requestContext) throws CedarProcessingException {
    String url =
        cedarConfig.getMicroserviceUrlUtil().getArtifact().getResourceType(nodeType) + "/"
            + CedarUrlUtil.urlEncode(artifactId);
    ClassicHttpResponse proxyResponse = ProxyUtil.proxyGet(url, requestContext);
    HttpEntity entity = proxyResponse.getEntity();
    int statusCode = proxyResponse.getCode();
    if (statusCode == HttpConstants.OK && entity != null) {
      try {
        String artifactString = EntityUtils.toString(entity, StandardCharsets.UTF_8);
        return Optional.of(JsonMapper.MAPPER.readTree(artifactString));
      } catch (IOException | ParseException e) {
        throw new CedarProcessingException("Error when reading artifact as Json: " + artifactId);
      }
    } else if (statusCode == HttpConstants.NOT_FOUND) {
      return Optional.empty();
    } else {
      throw new CedarProcessingException("Error when retrieving artifact: " + artifactId
          + " (the artifact server answered " + statusCode + ")");
    }
  }

}
