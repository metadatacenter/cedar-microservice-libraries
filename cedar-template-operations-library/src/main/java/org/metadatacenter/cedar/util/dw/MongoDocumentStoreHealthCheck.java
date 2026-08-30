package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.server.service.DiagnosticsService;

/**
 * Readiness check for the document store behind the artifact services.
 *
 * <p>Registered by {@link CedarMicroserviceApplicationWithMongo} for every server that opens the
 * store, rather than by each server for itself. Only the artifact server had ever registered it, so
 * repo, openview and monitor answered a health check that could not fail while every artifact read
 * through them did.
 */
public class MongoDocumentStoreHealthCheck extends HealthCheck {

  private final DiagnosticsService<JsonNode> diagnosticsService;

  public MongoDocumentStoreHealthCheck(DiagnosticsService<JsonNode> diagnosticsService) {
    this.diagnosticsService = diagnosticsService;
  }

  @Override
  protected Result check() {
    JsonNode heartbeat = diagnosticsService.heartbeat();
    if (heartbeat != null && heartbeat.path("storageServerConnection").asBoolean(false)) {
      return Result.healthy();
    }
    String detail = heartbeat == null ? "Mongo heartbeat returned no result" : heartbeat.toString();
    return Result.unhealthy(detail);
  }
}
