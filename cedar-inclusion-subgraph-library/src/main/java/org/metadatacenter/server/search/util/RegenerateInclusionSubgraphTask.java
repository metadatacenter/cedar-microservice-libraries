package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.JsonSchemaConstants;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.proxy.ArtifactProxy;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
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

public class RegenerateInclusionSubgraphTask {

  private static final Logger log = LoggerFactory.getLogger(RegenerateInclusionSubgraphTask.class);

  private final static int BATCH_SIZE = 100;

  private final CedarConfig cedarConfig;


  public RegenerateInclusionSubgraphTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void regenerateInclusionSubgraph(CedarRequestContext cedarAdminRequestContext) throws CedarProcessingException {

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(cedarAdminRequestContext);
    InclusionSubgraphServiceSession inclusionSubgraphSession = CedarDataServices.getInclusionSubgraphServiceSession(cedarAdminRequestContext);

    List<CedarResourceType> resourceTypeList = new ArrayList<>(List.of(CedarResourceType.TEMPLATE, CedarResourceType.ELEMENT));
    ResourceVersionFilter version = ResourceVersionFilter.ALL;
    ResourcePublicationStatusFilter publicationStatus = ResourcePublicationStatusFilter.ALL;
    List<String> sortList = new ArrayList<>(List.of(Neo4JUtil.escapePropertyName(QuerySortOptions.DEFAULT_SORT_FIELD.getFieldName())));

    long total = folderSession.viewAllCount(resourceTypeList, version, publicationStatus);
    log.warn("INCLUSION-SUBGRAPH Total count:" + total);
    int limit = BATCH_SIZE;
    int offset = 0;
    int retrievedCount = 0;
    do {
      List<FolderServerResourceExtract> folderServerResourceExtracts = folderSession.viewAll(resourceTypeList, version, publicationStatus, limit, offset, sortList);
      for (FolderServerResourceExtract resource : folderServerResourceExtracts) {
        this.updateResourceInclusionInfo(cedarAdminRequestContext, resource, inclusionSubgraphSession);
      }
      offset += limit;
      retrievedCount = folderServerResourceExtracts.size();
      log.warn("INCLUSION-SUBGRAPH Offset:" + offset + ", retrieved count:" + retrievedCount);
      System.out.println();
    } while (retrievedCount > 0);
  }

  private void updateResourceInclusionInfo(CedarRequestContext cedarAdminRequestContext, FolderServerResourceExtract resource, InclusionSubgraphServiceSession inclusionSubgraphSession) {
    try {
      Response responseFromArtifact = ArtifactProxy.executeResourceGetByProxyFromArtifactServer(cedarConfig.getMicroserviceUrlUtil(), null, resource.getType(), resource.getId(), Optional.empty(),
          cedarAdminRequestContext);
      InputStream is = (InputStream) responseFromArtifact.getEntity();
      JsonNode entityJsonNode = JsonMapper.MAPPER.readTree(is);
      List<String> includedIds = extractFirstLevelIncludedIds(entityJsonNode);
      inclusionSubgraphSession.updateInclusionArcs(resource.getResourceId(), includedIds);
    } catch (CedarProcessingException e) {
      log.error("Error while retrieving artifact from artifact server", e);
    } catch (RuntimeException | IOException e) {
      log.error("Error while processing artifact response from artifact server", e);
    }

  }

  private List<String> extractFirstLevelIncludedIds(JsonNode artifact) {
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
