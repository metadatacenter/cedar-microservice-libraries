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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public abstract class CedarMicroserviceApplicationWithMongo<T extends CedarMicroserviceConfiguration>
    extends CedarMicroserviceApplication<T> {

  protected static TemplateFieldService<String, JsonNode> templateFieldService;
  protected static TemplateElementService<String, JsonNode> templateElementService;
  protected static TemplateService<String, JsonNode> templateService;
  protected static TemplateInstanceService<String, JsonNode> templateInstanceService;
  protected MongoDocumentStoreHealthCheck mongoHealthCheck;
  protected ArtifactIdIndexHealthCheck artifactIdIndexHealthCheck;
  private ArtifactIdIndexProbe artifactIdIndexProbe;

  private static final Logger logger = LoggerFactory.getLogger(CedarMicroserviceApplicationWithMongo.class);
  private static final long INDEX_PROBE_TIMEOUT_SECONDS = 5;

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

    artifactIdIndexProbe = new ArtifactIdIndexProbe(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        List.of(
            artifactServerConfig.getMongoCollectionName(CedarResourceType.FIELD),
            artifactServerConfig.getMongoCollectionName(CedarResourceType.ELEMENT),
            artifactServerConfig.getMongoCollectionName(CedarResourceType.TEMPLATE),
            artifactServerConfig.getMongoCollectionName(CedarResourceType.INSTANCE)));
    artifactIdIndexHealthCheck = new ArtifactIdIndexHealthCheck(artifactIdIndexProbe);
  }

  /**
   * Says once, at startup, what the store's @id indexes are, so an unprovisioned store is on the
   * record from the first line of the log rather than only in a health response nobody reads.
   *
   * <p>Bounded and off the startup thread: a store that is slow to answer, or does not answer,
   * leaves the server starting normally. The probe reads index metadata and creates nothing, so
   * there is no partial effect to undo when it times out.
   */
  private void reportArtifactIdIndexes() {
    try {
      ArtifactIdIndexProbe.Status status =
          CompletableFuture.supplyAsync(artifactIdIndexProbe::inspect)
              .get(INDEX_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
      if (status.complete()) {
        logger.info("Artifact document store: {}", status.describe());
      } else {
        logger.error("Artifact document store: {}", status.describe());
      }
    } catch (TimeoutException e) {
      logger.warn("Artifact document store: @id index state not read within {} seconds",
          INDEX_PROBE_TIMEOUT_SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (RuntimeException | java.util.concurrent.ExecutionException e) {
      logger.warn("Artifact document store: @id index state could not be read", e);
    }
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
    if (artifactIdIndexHealthCheck != null) {
      environment.healthChecks().register("artifactIdIndex", artifactIdIndexHealthCheck);
      reportArtifactIdIndexes();
    }
  }

}
