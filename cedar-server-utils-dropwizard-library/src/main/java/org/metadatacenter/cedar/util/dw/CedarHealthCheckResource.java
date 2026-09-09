package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.annotation.Timed;
import com.codahale.metrics.health.HealthCheck;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.json.HealthCheckModule;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.util.http.CedarError;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.SortedMap;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

/**
 * This server's own health checks, on the application connector.
 *
 * <p>Dropwizard serves the same results on the admin connector, which
 * {@link CedarMicroserviceApplication} binds to loopback so that {@code /metrics} and
 * {@code /threads} cannot be read from the network. That binding is right and stays. It does mean
 * the admin connector answers nobody outside the process's own host, and under Compose every
 * server is its own host, so the Monitor server's cross-service health page could reach no server
 * but itself. Publishing health here gives that page a route without widening the admin connector.
 *
 * <p>The route is gated on {@code MONITOR_READ}, the permission {@link CedarServerInsightReportResource}
 * uses for the same reason: nginx proxies the application connector to a public host, and a health
 * report names every dependency a server holds and quotes the error text when one is unreachable.
 * The Monitor reaches the other servers container-to-container and forwards the caller's own
 * credential, so the gate costs its own page nothing.
 *
 * <p>The body and status are Dropwizard's: the same serialization the admin servlet uses, 200 when
 * every check passes and 500 when one does not, so a caller cannot tell the two routes apart.
 */
@Path("/healthcheck")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Service diagnostics")
@SecurityRequirement(name = "api_key")
public class CedarHealthCheckResource extends CedarMicroserviceResource {

  private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new HealthCheckModule());

  private final HealthCheckRegistry healthChecks;

  public CedarHealthCheckResource(CedarConfig cedarConfig, HealthCheckRegistry healthChecks) {
    super(cedarConfig);
    this.healthChecks = healthChecks;
  }

  @GET
  @Timed
  @Operation(summary = "Run this service's health checks")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Every health check passed",
          content = @Content(schema = @Schema(type = "object"))),
      @ApiResponse(responseCode = "401", description = "Unauthorized",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "403", description = "Forbidden",
          content = @Content(schema = @Schema(implementation = CedarError.class))),
      @ApiResponse(responseCode = "500", description = "At least one health check failed",
          content = @Content(schema = @Schema(type = "object")))
  })
  public Response healthCheck() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.MONITOR_READ);

    SortedMap<String, HealthCheck.Result> results = healthChecks.runHealthChecks();
    return Response.status(allHealthy(results) ? Response.Status.OK : Response.Status.INTERNAL_SERVER_ERROR)
        .type(MediaType.APPLICATION_JSON)
        .entity(serialize(results))
        .build();
  }

  static boolean allHealthy(SortedMap<String, HealthCheck.Result> results) {
    return results.values().stream().allMatch(HealthCheck.Result::isHealthy);
  }

  private static String serialize(SortedMap<String, HealthCheck.Result> results) throws CedarException {
    try {
      return MAPPER.writeValueAsString(results);
    } catch (Exception e) {
      throw new CedarProcessingException("The health check report could not be serialized", e);
    }
  }
}
