package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.elasticsearch.client.Client;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.search.IndexedDocumentType;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.document.IndexingDocumentNode;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.server.search.util.IndexUtils;
import org.metadatacenter.util.StringUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeIndexingService extends AbstractIndexingService {

  private static final Logger log = LoggerFactory.getLogger(NodeIndexingService.class);

  private ElasticsearchIndexingWorker indexWorker;

  NodeIndexingService(String indexName, CedarConfig cedarConfig, Client client) {
    indexWorker = new ElasticsearchIndexingWorker(indexName, cedarConfig.getElasticsearchConfig(), client,
        IndexedDocumentType.NODE);
  }

  public IndexedDocumentId indexDocument(String resourceId, String name) throws CedarProcessingException {
    log.debug("Indexing node (id = " + resourceId + ")");
    IndexingDocumentNode ir = new IndexingDocumentNode(resourceId, StringUtil.comparisonValue(name));
    JsonNode jsonResource = JsonMapper.MAPPER.convertValue(ir, JsonNode.class);
    return indexWorker.addToIndex(jsonResource, null);
  }

  public long removeDocumentFromIndex(String resourceId) throws CedarProcessingException {
    if (resourceId != null) {
      log.debug("Removing node from index (id = " + resourceId);
      return indexWorker.removeAllFromIndex(resourceId, null);
    } else {
      return -1;
    }
  }

  public void removeDocumentFromIndex(IndexedDocumentId indexedDocumentId) {
    log.debug("Removing node from index (_id = " + indexedDocumentId.getId());
    indexWorker.removeFromIndex(indexedDocumentId);
  }

}
