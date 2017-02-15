package org.metadatacenter.server.search.elasticsearch.service;

import org.elasticsearch.client.Client;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.search.IndexedDocumentType;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchSearchingWorker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class NodeSearchingService extends AbstractSearchingService {

  private static final Logger log = LoggerFactory.getLogger(NodeSearchingService.class);

  private final Client client;
  private final ElasticsearchConfig config;
  private ElasticsearchSearchingWorker searchWorker;

  NodeSearchingService(CedarConfig cedarConfig, Client client) {
    this.client = client;
    this.config = cedarConfig.getElasticsearchConfig();
    searchWorker = new ElasticsearchSearchingWorker(cedarConfig.getElasticsearchConfig(), client, IndexedDocumentType.NODE);
  }

  public IndexedDocumentId getByCedarId(String resourceId) throws CedarProcessingException {
    return getByCedarId(client, resourceId, config.getIndexName(), config.getType(IndexedDocumentType.NODE));
  }

  public List<String> findAllValuesForField(String fieldName) throws CedarProcessingException {
    return searchWorker.findAllValuesForField(fieldName);
  }

}
