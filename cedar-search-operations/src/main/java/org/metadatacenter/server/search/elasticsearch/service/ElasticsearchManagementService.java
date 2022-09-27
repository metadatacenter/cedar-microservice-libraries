package org.metadatacenter.server.search.elasticsearch.service;

import org.elasticsearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.elasticsearch.action.admin.indices.create.CreateIndexResponse;
import org.elasticsearch.action.admin.indices.delete.DeleteIndexRequest;
import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.collect.ImmutableOpenMap;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ElasticsearchConfig;
import org.metadatacenter.config.ElasticsearchMappingsConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.search.IndexedDocumentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ElasticsearchManagementService {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchManagementService.class);

  private final ElasticsearchConfig config;
  private final Settings settings;
  private final Map<String, Object> searchIndexSettings;
  private final Map<String, Object> rulesIndexSettings;
  private final ElasticsearchMappingsConfig searchIndexMappings;
  private final ElasticsearchMappingsConfig rulesIndexMappings;
  private Client elasticClient = null;

  public ElasticsearchManagementService(ElasticsearchConfig config, CedarConfig cedarConfig) {
    this.config = config;
    this.searchIndexSettings = (cedarConfig.getSearchSettingsMappingsConfig().getSettings());
    this.rulesIndexSettings = cedarConfig.getRulesSettingsMappingsConfig().getSettings();
    this.searchIndexMappings = cedarConfig.getSearchSettingsMappingsConfig().getMappings();
    this.rulesIndexMappings = cedarConfig.getRulesSettingsMappingsConfig().getMappings();
    this.settings = Settings.builder().put("cluster.name", config.getClusterName()).build();
  }

  Client getClient() {
    try {
      if (elasticClient == null) {
        elasticClient = new PreBuiltTransportClient(settings)
            .addTransportAddress(new TransportAddress(InetAddress.getByName(config.getHost()),
                config.getTransportPort()));
      }
      return elasticClient;
    } catch (Exception e) {
      log.error("There was an error creating the elasticsearch client", e);
      return null;
    }
  }

  public void closeClient() {
    elasticClient.close();
  }

  public void createSearchIndex(String indexName) throws CedarProcessingException {
    createIndex(indexName, searchIndexSettings, searchIndexMappings);
  }

  public void createRulesIndex(String indexName) throws CedarProcessingException {
    createIndex(indexName, rulesIndexSettings, rulesIndexMappings);
  }

  private void createIndex(String indexName, Map<String, Object> indexSettings,
                           ElasticsearchMappingsConfig indexMappings)
      throws CedarProcessingException {

    Client client = getClient();
    CreateIndexRequestBuilder createIndexRequestBuilder = client.admin().indices().prepareCreate(indexName);
    // Set settings
    if (indexSettings != null) {
      createIndexRequestBuilder.setSettings(indexSettings);
    }
    // Put mappings
    if (indexMappings.getDoc() != null) {
      createIndexRequestBuilder.addMapping(IndexedDocumentType.DOC.getValue(), indexMappings.getDoc());
    }
    // Create index
    CreateIndexResponse response = createIndexRequestBuilder.execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new CedarProcessingException("Failed to create the index " + indexName);
    } else {
      log.info("The index " + indexName + " has been created");
    }
  }

  public boolean indexExists(String indexName) {
    return getClient().admin().indices().prepareExists(indexName).execute().actionGet().isExists();
  }

  public boolean deleteIndex(String indexName) throws CedarProcessingException {
    AcknowledgedResponse deleteIndexResponse =
        getClient().admin().indices().delete(new DeleteIndexRequest(indexName)).actionGet();
    if (!deleteIndexResponse.isAcknowledged()) {
      throw new CedarProcessingException("Failed to delete index '" + indexName + "'");
    } else {
      log.info("The index '" + indexName + "' has been deleted");
      return true;
    }
  }

  public boolean addAlias(String indexName, String aliasName) throws CedarProcessingException {
    AcknowledgedResponse response = getClient().admin().indices().prepareAliases()
        .addAlias(indexName, aliasName)
        .execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new CedarProcessingException("Failed to add alias '" + aliasName + "' to index '" + indexName + "'");
    } else {
      log.info("The alias '" + aliasName + "' has been added to index '" + indexName + "'");
      return true;
    }
  }

  public boolean deleteAlias(String indexName, String aliasName) throws CedarProcessingException {
    AcknowledgedResponse response = getClient().admin().indices().prepareAliases()
        .removeAlias(indexName, aliasName)
        .execute().actionGet();
    if (!response.isAcknowledged()) {
      throw new CedarProcessingException("Failed to remove alias '" + aliasName + "' from index '" + indexName + "'");
    } else {
      log.info("The alias '" + aliasName + "' has been removed from the index '" + indexName + "'");
      return true;
    }
  }

  public List<String> getAllIndices() {
    List<String> indexNames = new ArrayList<>();
    try {
      ImmutableOpenMap<String, IndexMetadata> indices = getClient().admin().cluster()
              .prepareState().execute()
              .actionGet().getState()
              .getMetadata().getIndices();
      for (IndexMetadata value : indices.values()) {
        indexNames.add(value.getIndex().getName());
      }
    } catch (Exception e) {
      log.error("There was an error retrieving existing indices");
    }
    return indexNames;
  }

}
