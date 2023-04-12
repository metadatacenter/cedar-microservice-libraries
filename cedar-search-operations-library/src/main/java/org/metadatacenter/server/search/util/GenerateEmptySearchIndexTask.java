package org.metadatacenter.server.search.util;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateEmptySearchIndexTask {

  private static final Logger log = LoggerFactory.getLogger(GenerateEmptySearchIndexTask.class);

  private final CedarConfig cedarConfig;

  public GenerateEmptySearchIndexTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void generateEmptySearchIndex(CedarRequestContext requestContext) throws CedarProcessingException {
    log.info("Regenerating empty search index.");

    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getSearchIndex().getName();

    try {
      // Create new index and set it up
      String newIndexName = indexUtils.getNewIndexName(aliasName);
      esManagementService.createSearchIndex(newIndexName);
      log.info("Empty search index created:" + newIndexName);
      // Point alias to new index
      esManagementService.addAlias(newIndexName, aliasName);

      // Delete any other index previously associated to the alias
      indexUtils.deleteOldIndices(esManagementService, aliasName, newIndexName);
    } catch (Exception e) {
      log.error("Error while generating empty index", e);
      throw new CedarProcessingException(e);
    }
  }

}
