package org.metadatacenter.server.search.util;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateEmptyRulesIndexTask {

  private static final Logger log = LoggerFactory.getLogger(GenerateEmptyRulesIndexTask.class);

  private final CedarConfig cedarConfig;

  public GenerateEmptyRulesIndexTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void generateEmptyRulesIndex(CedarRequestContext requestContext) throws CedarProcessingException {
    log.info("Regenerating empty rules index.");

    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getRulesIndex().getName();

    try {
      // Create new index and set it up
      String newIndexName = indexUtils.getNewIndexName(aliasName);
      esManagementService.createRulesIndex(newIndexName);
      log.info("Empty rules index created:" + newIndexName);
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
