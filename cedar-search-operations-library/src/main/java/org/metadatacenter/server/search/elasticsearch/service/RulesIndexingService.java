package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.elasticsearch.client.Client;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;

import static org.metadatacenter.constant.ElasticsearchConstants.TEMPLATE_ID;

public class RulesIndexingService extends AbstractIndexingService {

  private static final Logger log = LoggerFactory.getLogger(RulesIndexingService.class);

  private final ElasticsearchIndexingWorker indexWorker;

  RulesIndexingService(String indexName, Client client) {
    indexWorker = new ElasticsearchIndexingWorker(indexName, client);
  }

  public void indexRules(JsonNode rules, String templateId) throws CedarProcessingException {
    log.info("Indexing template rules for template: " + templateId);
    for (JsonNode rule : rules) {
      indexRule(rule);
    }
  }

  public IndexedDocumentId indexRule(JsonNode rule) throws CedarProcessingException {
    return indexWorker.addToIndex(rule);
  }

  public long removeRulesFromIndex(String templateId) throws CedarProcessingException {
    if (templateId != null) {
      return indexWorker.removeAllFromIndex(TEMPLATE_ID, templateId);
    } else {
      throw new CedarProcessingException("templateId is null");
    }
  }


}
