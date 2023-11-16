package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.JsonSchemaConstants;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.proxy.ArtifactProxy;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.util.ModelUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

public class InclusionSubgraphUtil {

  private static final Logger log = LoggerFactory.getLogger(InclusionSubgraphUtil.class);

  private InclusionSubgraphUtil() {
  }

  public static void updateResourceInclusionInfo(CedarRequestContext context, CedarConfig cedarConfig, FolderServerResourceExtract resource, InclusionSubgraphServiceSession inclusionSubgraphSession) {
    Response responseFromArtifact = null;
    try {
      responseFromArtifact = ArtifactProxy.executeResourceGetByProxyFromArtifactServer(cedarConfig.getMicroserviceUrlUtil(), null, resource.getType(), resource.getId(), Optional.empty(),
          context);
      InputStream is = (InputStream) responseFromArtifact.getEntity();
      JsonNode entityJsonNode = JsonMapper.MAPPER.readTree(is);
      updateResourceInclusionInfo(resource, inclusionSubgraphSession, entityJsonNode);
    } catch (CedarProcessingException e) {
      log.error("Error while retrieving artifact from artifact server", e);
    } catch (RuntimeException | IOException e) {
      log.error("Error while processing artifact response from artifact server", e);
    }
  }

  public static void updateResourceInclusionInfo(FolderServerResourceExtract resource, InclusionSubgraphServiceSession inclusionSubgraphSession, JsonNode entityJsonNode) {
    List<String> includedIds = extractFirstLevelIncludedIds(entityJsonNode);
    inclusionSubgraphSession.updateInclusionArcs(resource.getResourceId(), includedIds);
  }

  private static List<String> extractFirstLevelIncludedIds(JsonNode artifact) {
    List<String> linkIds = new ArrayList<>();
    JsonNode properties = artifact.get(JsonSchemaConstants.PROPERTIES);
    for (Iterator<String> it = properties.fieldNames(); it.hasNext(); ) {
      String fieldName = it.next();
      JsonNode embedded = null;
      if (!ModelUtil.isSpecialField(fieldName)) {
        JsonNode candidate = properties.get(fieldName);
        JsonNode typeNode = candidate.get(JsonSchemaConstants.TYPE);
        String typeValue = typeNode.textValue();
        if (JsonSchemaConstants.TYPE_VALUE_OBJECT.equals(typeValue)) {
          // single embedded artifact
          embedded = candidate;
        } else if (JsonSchemaConstants.TYPE_VALUE_ARRAY.equals(typeValue)) {
          // multi embedded artifact
          embedded = candidate.get(JsonSchemaConstants.ITEMS);
        }
      }
      if (embedded != null) {
        JsonNode atTypeNode = embedded.get(LinkedData.TYPE);
        String atType = atTypeNode.textValue();
        JsonNode atIdNode = embedded.get(LinkedData.ID);
        String atId = atIdNode.textValue();
        if (CedarConstants.TEMPLATE_FIELD_TYPE_URI.equals(atType)) {
          linkIds.add(atId);
        } else if (CedarConstants.TEMPLATE_ELEMENT_TYPE_URI.equals(atType)) {
          linkIds.add(atId);
        }
      }
    }
    return linkIds;
  }
}
