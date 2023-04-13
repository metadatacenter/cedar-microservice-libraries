package org.metadatacenter.server.search.util;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadValueSetsOntologyTask {
  private static final Logger log = LoggerFactory.getLogger(GenerateEmptyRulesIndexTask.class);

  private final CedarConfig cedarConfig;

  public LoadValueSetsOntologyTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void loadValueSetsOntology(CedarRequestContext requestContext) throws CedarProcessingException
  {
    log.info("Loading value sets ontology.");
    IndexUtils indexUtils = new IndexUtils(cedarConfig);

    try {
      indexUtils.loadValueSetsOntology();
    } catch (Exception e) {
      log.warn("Failed to load value sets ontology: " + e.getMessage());
      throw new CedarProcessingException(e);
    }
  }

}
