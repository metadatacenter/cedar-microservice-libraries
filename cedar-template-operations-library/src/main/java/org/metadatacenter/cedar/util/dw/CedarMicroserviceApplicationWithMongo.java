package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import io.dropwizard.core.setup.Environment;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.service.TemplateElementService;
import org.metadatacenter.server.service.TemplateFieldService;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.DiagnosticsServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateFieldServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;

public abstract class CedarMicroserviceApplicationWithMongo<T extends CedarMicroserviceConfiguration>
    extends CedarMicroserviceApplication<T> {

  protected static TemplateFieldService<String, JsonNode> templateFieldService;
  protected static TemplateElementService<String, JsonNode> templateElementService;
  protected static TemplateService<String, JsonNode> templateService;
  protected static TemplateInstanceService<String, JsonNode> templateInstanceService;
  protected MongoDocumentStoreHealthCheck mongoHealthCheck;

  protected void initMongoServices(MongoClient mongoClientForDocuments, MongoConfig artifactServerConfig) {
    templateFieldService = new TemplateFieldServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.FIELD));

    templateElementService = new TemplateElementServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.TEMPLATE));

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.INSTANCE));

    mongoHealthCheck = new MongoDocumentStoreHealthCheck(
        new DiagnosticsServiceMongoDB(mongoClientForDocuments, artifactServerConfig.getDatabaseName()));
  }

  /**
   * Registers the document-store probe alongside the shared setup, so opening the store and
   * reporting on it are one step. A server that calls {@link #initMongoServices} cannot now publish
   * a health endpoint that stays green while the store it reads is unreachable.
   */
  @Override
  protected void setupEnvironment(Environment environment) {
    super.setupEnvironment(environment);
    if (mongoHealthCheck != null) {
      environment.healthChecks().register("mongo", mongoHealthCheck);
    }
  }

}
