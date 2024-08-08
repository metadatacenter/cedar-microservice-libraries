package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.config.OpensearchMappingsConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.action.admin.indices.alias.IndicesAliasesRequest;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.support.master.AcknowledgedResponse;
import org.opensearch.client.*;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.client.indices.CreateIndexResponse;
import org.opensearch.client.indices.GetIndexRequest;
import org.opensearch.client.indices.GetIndexResponse;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class ElasticsearchManagementService {

  private static final Logger log = LoggerFactory.getLogger(ElasticsearchManagementService.class);

  private final OpensearchConfig config;
  private final Settings settings;
  private final Map<String, Object> searchIndexSettings;
  private final Map<String, Object> rulesIndexSettings;
  private final OpensearchMappingsConfig searchIndexMappings;
  private final OpensearchMappingsConfig rulesIndexMappings;
  private RestHighLevelClient elasticClient = null;

  public ElasticsearchManagementService(OpensearchConfig config, CedarConfig cedarConfig) {
    System.setProperty("es.set.netty.runtime.available.processors", "false");
    this.config = config;
    this.searchIndexSettings = (cedarConfig.getSearchSettingsMappingsConfig().getSettings());
    this.rulesIndexSettings = cedarConfig.getRulesSettingsMappingsConfig().getSettings();
    this.searchIndexMappings = cedarConfig.getSearchSettingsMappingsConfig().getMappings();
    this.rulesIndexMappings = cedarConfig.getRulesSettingsMappingsConfig().getMappings();
    this.settings = Settings.builder().put("cluster.name", config.getClusterName()).build();
  }

  RestHighLevelClient getClient() {
    try {
      if (elasticClient == null) {
        RestClientBuilder builder = RestClient.builder(
            new HttpHost(config.getHost(), config.getRestPort(), "http"));
        elasticClient = new RestHighLevelClient(builder);
      }
      return elasticClient;
    } catch (Exception e) {
      log.error("There was an error creating the OpenSearch client", e);
      return null;
    }
  }

  public void closeClient() {
    if (elasticClient != null) {
      try {
        elasticClient.close();
      } catch (IOException e) {
        log.error("Error closing the OpenSearch client", e);
      } finally {
        elasticClient = null;
      }
    }
  }

  public void createSearchIndex(String indexName) throws CedarProcessingException {
    createIndex(indexName, searchIndexSettings, searchIndexMappings);
  }

  public void createRulesIndex(String indexName) throws CedarProcessingException {
    createIndex(indexName, rulesIndexSettings, rulesIndexMappings);
  }

  private void createIndex(String indexName, Map<String, Object> indexSettings,
                           OpensearchMappingsConfig indexMappings) throws CedarProcessingException {

    RestHighLevelClient client = getClient();
    CreateIndexRequest createIndexRequest = new CreateIndexRequest(indexName);

    // Set settings
    if (indexSettings != null) {
      createIndexRequest.settings(Settings.builder().loadFromMap(indexSettings));
    }

    // Put mappings
    if (indexMappings.getDoc() != null) {
      try {
        String mappingsJson = JsonMapper.MAPPER.writeValueAsString(indexMappings.getDoc());
        createIndexRequest.mapping(mappingsJson, XContentType.JSON);
      } catch (IOException e) {
        throw new CedarProcessingException("Error converting mappings to JSON", e);
      }
    }

    try {
      IndicesClient indicesClient = client.indices();
      CreateIndexResponse createIndexResponse = indicesClient.create(createIndexRequest, RequestOptions.DEFAULT);

      if (!createIndexResponse.isAcknowledged()) {
        throw new CedarProcessingException("Failed to create the index " + indexName);
      } else {
        log.info("The index " + indexName + " has been created");
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error creating the index " + indexName, e);
    }
  }

  public boolean indexExists(String indexName) {
    GetIndexRequest request = new GetIndexRequest(indexName);
    try {
      return getClient().indices().exists(request, RequestOptions.DEFAULT);
    } catch (IOException e) {
      log.error("Error checking if index exists: " + indexName, e);
      return false;
    }
  }

  public boolean deleteIndex(String indexName) throws CedarProcessingException {
    DeleteIndexRequest request = new DeleteIndexRequest(indexName);
    try {
      AcknowledgedResponse deleteIndexResponse = getClient().indices().delete(request, RequestOptions.DEFAULT);
      if (!deleteIndexResponse.isAcknowledged()) {
        throw new CedarProcessingException("Failed to delete index '" + indexName + "'");
      } else {
        log.info("The index '" + indexName + "' has been deleted");
        return true;
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error deleting index '" + indexName + "'", e);
    }
  }

  public boolean addAlias(String indexName, String aliasName) throws CedarProcessingException {
    IndicesAliasesRequest request = new IndicesAliasesRequest();
    IndicesAliasesRequest.AliasActions aliasAction = new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.ADD)
        .index(indexName)
        .alias(aliasName);
    request.addAliasAction(aliasAction);

    try {
      AcknowledgedResponse response = getClient().indices().updateAliases(request, RequestOptions.DEFAULT);
      if (!response.isAcknowledged()) {
        throw new CedarProcessingException("Failed to add alias '" + aliasName + "' to index '" + indexName + "'");
      } else {
        log.info("The alias '" + aliasName + "' has been added to index '" + indexName + "'");
        return true;
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error adding alias '" + aliasName + "' to index '" + indexName + "'", e);
    }
  }

  public boolean deleteAlias(String indexName, String aliasName) throws CedarProcessingException {
    IndicesAliasesRequest request = new IndicesAliasesRequest();
    IndicesAliasesRequest.AliasActions aliasAction = new IndicesAliasesRequest.AliasActions(IndicesAliasesRequest.AliasActions.Type.REMOVE)
        .index(indexName)
        .alias(aliasName);
    request.addAliasAction(aliasAction);

    try {
      AcknowledgedResponse response = getClient().indices().updateAliases(request, RequestOptions.DEFAULT);
      if (!response.isAcknowledged()) {
        throw new CedarProcessingException("Failed to remove alias '" + aliasName + "' from index '" + indexName + "'");
      } else {
        log.info("The alias '" + aliasName + "' has been removed from the index '" + indexName + "'");
        return true;
      }
    } catch (IOException e) {
      throw new CedarProcessingException("Error removing alias '" + aliasName + "' from index '" + indexName + "'", e);
    }
  }

  public List<String> getAllIndices() {
    List<String> indexNames = new ArrayList<>();
    try {
      GetIndexRequest request = new GetIndexRequest("*");
      GetIndexResponse response = getClient().indices().get(request, RequestOptions.DEFAULT);
      indexNames.addAll(Arrays.asList(response.getIndices()));
    } catch (IOException e) {
      log.error("There was an error retrieving existing indices", e);
    }
    return indexNames;
  }

}
