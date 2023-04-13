package org.metadatacenter.server.search.util;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class RegenerateRulesIndexTask {

  private static final Logger log = LoggerFactory.getLogger(RegenerateRulesIndexTask.class);

  private final CedarConfig cedarConfig;


  public RegenerateRulesIndexTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void ensureRulesIndexExists() throws CedarProcessingException {
    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getRulesIndex().getName();

    indexUtils.ensureIndexAndAliasExist(esManagementService, aliasName);
  }

  public void regenerateRulesIndex(boolean force, CedarRequestContext requestContext) throws CedarProcessingException {
    log.info("Regenerating rules index. Force:" + force);

    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getRulesIndex().getName();

    boolean regenerate = true;
    try {
      // Get all resources
      log.info("Reading all resources from the existing index.");
      List<FileSystemResource> resources = indexUtils.findAllResources(requestContext);
      // Checks if is necessary to regenerate the index or not
      if (!force) {
        log.info("Checking if it is necessary to regenerate the rules index from DB");
        // Check if the index exists (using the alias). If it exists, check if it contains all resources
        if (esManagementService.indexExists(aliasName)) {
          log.warn("The search index/alias '" + aliasName + "' is present!");
//          // Use the artifact ids to check if the resources in the DBs and in the index are different
//          List<String> dbResourceIds = getResourceIds(resources);
//          log.info("No. of nodes in DB that are expected to be indexed: " + dbResourceIds.size());
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
//              log.info("DB and search index match. It is not necessary to regenerate the index");
//            } else {
//              log.warn("DB and search index do not match! (different ids)");
//            }
//          } else {
//            log.warn("DB and search index do not match! (different size)");
//          }
        } else {
          log.warn("The search index/alias '" + aliasName + "' does not exist!");
        }
      } else {
        log.info("Force is true. It is not needed to compare content");
      }
      if (regenerate) {
        log.info("After all the checks were performed, it seems that the index needs to be regenerated!");
        // Create new index and set it up
        String newIndexName = indexUtils.getNewIndexName(aliasName);
        esManagementService.createRulesIndex(newIndexName);
        log.info("Rules index created:" + newIndexName);

        //NodeIndexingService nodeIndexingService = esServiceFactory.nodeIndexingService(newIndexName);

        // Get resources content and index it
//        int count = 1;
//        for (FolderServerNode resource : resources) {
//          try {
//            CedarNodeMaterializedPermissions perm = null;
//            if (node.getType() == CedarResourceType.FOLDER) {
//              perm = permissionSession.getNodeMaterializedPermission(node.getId(), FolderOrResource.FOLDER);
//            } else {
//              perm = permissionSession.getNodeMaterializedPermission(resource.getId(), FolderOrResource.RESOURCE);
//            }
//            IndexedDocumentId indexedNodeId = nodeIndexingService.indexDocument(resource, perm);
//
//            if (count % 100 == 0) {
//              float progress = (100 * count++) / resources.size();
//              log.info(String.format("Progress: %.0f%%", progress));
//            }
//          } catch (Exception e) {
//            log.error("Error while indexing document: " + resource.getId(), e);
//          }
//        }
        // Point alias to new index
        esManagementService.addAlias(newIndexName, aliasName);

        // Delete any other index previously associated to the alias
        indexUtils.deleteOldIndices(esManagementService, aliasName, newIndexName);
      }
    } catch (Exception e) {
      log.error("Error while regenerating index", e);
      throw new CedarProcessingException(e);
    }
  }

}
