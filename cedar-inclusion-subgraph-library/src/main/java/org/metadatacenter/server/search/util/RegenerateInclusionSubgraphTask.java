package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.JsonSchemaConstants;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.proxy.ArtifactProxy;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
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

  private final static int BATCH_SIZE = 1000;

  private final CedarConfig cedarConfig;


  public RegenerateInclusionSubgraphTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void regenerateInclusionSubgraph(CedarRequestContext cedarAdminRequestContext) throws CedarProcessingException {

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(cedarAdminRequestContext);

    List<CedarResourceType> resourceTypeList = new ArrayList<>(List.of(CedarResourceType.TEMPLATE, CedarResourceType.ELEMENT));
    ResourceVersionFilter version = ResourceVersionFilter.ALL;
    ResourcePublicationStatusFilter publicationStatus = ResourcePublicationStatusFilter.ALL;
    List<String> sortList = new ArrayList<>(List.of(Neo4JUtil.escapePropertyName(QuerySortOptions.DEFAULT_SORT_FIELD.getFieldName())));

    long total = folderSession.viewAllCount(resourceTypeList, version, publicationStatus);
    System.out.println("Total count:" + total);
    int limit = BATCH_SIZE;
    int offset = 0;
    int retrievedCount = 0;
    do {
      List<FolderServerResourceExtract> folderServerResourceExtracts = folderSession.viewAll(resourceTypeList, version, publicationStatus, limit, offset, sortList);
      for (FolderServerResourceExtract resource : folderServerResourceExtracts) {
        this.updateResourceInclusionInfo(cedarAdminRequestContext, resource);
      }
      offset += limit;
      retrievedCount = folderServerResourceExtracts.size();
      System.out.println("Offset:" + offset + ", retrieved count:" + retrievedCount);
    } while (retrievedCount > 0);
//
//
//    IndexUtils indexUtils = new IndexUtils(cedarConfig);
//    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();
//    NodeSearchingService nodeSearchingService = indexUtils.getNodeSearchingService();
//
//    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getSearchIndex().getName();
//    NodeIndexingService nodeIndexingService = null;
//
//    boolean regenerate = true;
//    try {
//      ResourcePermissionServiceSession permissionSession = CedarDataServices.getResourcePermissionServiceSession(requestContext);
//      CategoryServiceSession categorySession = CedarDataServices.getCategoryServiceSession(requestContext);
//      // Get all resources
//      log.info("Reading all resources from the existing search index.");
//      List<FileSystemResource> resources = indexUtils.findAllResources(requestContext);
//      // Checks if is necessary to regenerate the index or not
//      if (!force) {
//        log.info("Force is false. Checking if it is necessary to regenerate the search index from Neo4j.");
//        // Check if the index exists (using the alias). If it exists, check if it contains all resources
//        if (esManagementService.indexExists(aliasName)) {
//          log.warn("The search index/alias '" + aliasName + "' is present!");
//          // Use the artifact ids to check if the resources in the DBs and in the index are different
//          List<String> dbResourceIds = getResourceIds(resources);
//          log.info("No. of nodes in Neo4j that are expected to be indexed: " + dbResourceIds.size());
//          List<String> indexResourceIds = nodeSearchingService.findAllValuesForField(DOCUMENT_CEDAR_ID);
//          log.info("No. of content document in the index: " + indexResourceIds.size());
//          if (dbResourceIds.size() == indexResourceIds.size()) {
//            // Compare the two lists
//            List<String> tmp1 = new ArrayList(dbResourceIds);
//            List<String> tmp2 = new ArrayList(indexResourceIds);
//            Collections.sort(tmp1);
//            Collections.sort(tmp2);
//            if (tmp1.equals(tmp2)) {
//              regenerate = false;
//              log.info("Neo4j and search index match. It is not necessary to regenerate the index");
//            } else {
//              log.warn("Neo4j and search index do not match! (different ids)");
//            }
//          } else {
//            log.warn("Neo4j and search index do not match! (different size)");
//          }
//        } else {
//          log.warn("The search index/alias '" + aliasName + "' does not exist!");
//        }
//      } else {
//        log.info("Force is true. It is not needed to compare the search index and Neo4j");
//      }
//      if (regenerate) {
//        log.info("After all the checks were performed, it seems that the index needs to be regenerated!");
//        // Create new index and set it up
//        String newIndexName = indexUtils.getNewIndexName(aliasName);
//        esManagementService.createSearchIndex(newIndexName);
//        log.info("Search index created:" + newIndexName);
//
//        nodeIndexingService = indexUtils.getNodeIndexingService(newIndexName);
//
//        // Get resources content and index it
//        int count = 1;
//        int batchCount = 1;
//        List<IndexingDocumentDocument> currentBatch = new ArrayList<>();
//        for (FileSystemResource node : resources) {
//          try {
//            CedarNodeMaterializedPermissions perm = permissionSession.getResourceMaterializedPermission(node.getResourceId());
//            CedarNodeMaterializedCategories categories = null;
//            if (node instanceof FolderServerArtifact) {
//              categories = categorySession.getArtifactMaterializedCategories((CedarArtifactId) node.getResourceId());
//            }
//            currentBatch.add(nodeIndexingService.createIndexDocument(node, perm, categories, requestContext, true));
//
//            if (count % 100 == 0) {
//              float progress = (100 * count++) / resources.size();
//              log.info(String.format("Progress: %.0f%%", progress));
//            }
//            if (currentBatch.size() >= BATCH_SIZE) {
//              log.info(String.format("Batch progress: %d", batchCount));
//              nodeIndexingService.indexBatch(currentBatch);
//              currentBatch.clear();
//            }
//            count++;
//            batchCount++;
//          } catch (Exception e) {
//            log.error("Error while indexing document: " + node.getId(), e);
//          }
//        }
//
//        log.info(String.format("Batch progress remaining: %d", currentBatch.size()));
//        if (currentBatch.size() > 0) {
//          nodeIndexingService.indexBatch(currentBatch);
//        }
//
//        // Point alias to new index
//        esManagementService.addAlias(newIndexName, aliasName);
//
//        // Delete any other index previously associated to the alias
//        indexUtils.deleteOldIndices(esManagementService, aliasName, newIndexName);
//      } else {
//        log.info(
//            "After all the checks were performed, it seems that the index does not need to be regenerated this time.");
//      }
//    } catch (Exception e) {
//      log.error("Error while regenerating index", e);
//      throw new CedarProcessingException(e);
//    } finally {
//      // Clear template nodes cache
//      nodeIndexingService.instanceContentExtractor.clearNodesCache();
//    }
  }

  private void updateResourceInclusionInfo(CedarRequestContext cedarAdminRequestContext, FolderServerResourceExtract resource) {
    try {
      Response responseFromArtifact = ArtifactProxy.executeResourceGetByProxyFromArtifactServer(cedarConfig.getMicroserviceUrlUtil(), null, resource.getType(), resource.getId(), Optional.empty(), cedarAdminRequestContext);
      InputStream is = (InputStream)  responseFromArtifact.getEntity();
      JsonNode entityJsonNode = JsonMapper.MAPPER.readTree(is);
      List<String> includedIds = extractFirstLevelIncludedIds(entityJsonNode);
      if (includedIds.size() > 0) {
        System.out.println("From: " + resource.getId());
        for (String includedId : includedIds) {
          System.out.println("               To: " + includedId);
        }
      }
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

//  private List<String> getResourceIds(List<FileSystemResource> resources) {
//    List<String> ids = new ArrayList<>();
//    for (FileSystemResource resource : resources) {
//      ids.add(resource.getId());
//    }
//    return ids;
//  }

}
