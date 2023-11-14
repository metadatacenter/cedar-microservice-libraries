package org.metadatacenter.server.search.elasticsearch.service;

import org.metadatacenter.config.CedarConfig;

public class ElasticsearchServiceFactory {

  private static ElasticsearchServiceFactory instance;

  private CedarConfig cedarConfig;
  private ElasticsearchManagementService managementService;

  private ElasticsearchServiceFactory() {
  }

  public static synchronized ElasticsearchServiceFactory getInstance(CedarConfig cedarConfig) {
    if (instance == null) {
      instance = new ElasticsearchServiceFactory();
      instance.init(cedarConfig);
    }
    return instance;
  }

  private void init(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    System.out.println("ElasticsearchServiceFactory.init");
    this.managementService = new ElasticsearchManagementService(cedarConfig.getElasticsearchConfig(), cedarConfig);
    System.out.println("Built client:");
    System.out.println(this.managementService.getClient());
    System.out.println("*********************** ------------------------------------------------------");
  }

  public ElasticsearchManagementService getManagementService() {
    return managementService;
  }

  public NodeIndexingService nodeIndexingService() {
    return nodeIndexingService(instance.cedarConfig.getElasticsearchConfig().getIndexes().getSearchIndex().getName());
  }

  public NodeIndexingService nodeIndexingService(String indexName) {
    return new NodeIndexingService(cedarConfig, indexName, instance.managementService.getClient());
  }

  public NodeSearchingService nodeSearchingService() {
    return new NodeSearchingService(instance.cedarConfig, instance.managementService.getClient());
  }

  public RulesIndexingService rulesIndexingService() {
    return rulesIndexingService(instance.cedarConfig.getElasticsearchConfig().getIndexes().getRulesIndex().getName());
  }

  public RulesIndexingService rulesIndexingService(String indexName) {
    return new RulesIndexingService(indexName, instance.managementService.getClient());
  }

}
