package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.annotation.Timed;
import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.CedarResolvedConfigurationReport;
import org.metadatacenter.config.environment.CedarEnvironmentReport;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.rest.assertion.GenericAssertions.LoggedIn;

/**
 * What one service was configured with and built from.
 *
 * <p>Every CEDAR service resolves its configuration from its own sandbox of environment variables,
 * and the three routes here report the stages of that: the variables that went in
 * ({@code /environment}), the configuration that came out ({@code /configuration}), and the code
 * that is running it ({@code /build}). Until now the first was written to the service's log at boot
 * and the other two to nowhere, so a variable missing from one container out of fifteen was found by
 * reading fifteen log files, and a box pulled but not rebuilt was found by noticing the symptom.
 *
 * <p>Published on the application connector, beside {@link CedarHealthCheckResource} and for the
 * same reason: the admin connector is bound to loopback, and under Docker every service is its own
 * host, so nothing the Monitor server can reach lives there. That connector is proxied by nginx to
 * a public host, so every route is gated on {@code MONITOR_READ}, which only {@code monitorManager}
 * carries — the Monitor reaches the other services container-to-container and forwards the caller's
 * own credential, so its own pages cost nothing for the gate.
 *
 * <p>No secret leaves here in the clear. Environment values are masked against
 * {@link org.metadatacenter.config.environment.CedarEnvironmentVariable#isSecure()}, and the resolved
 * configuration is masked both by key name and by value. The sandbox boundary is kept too: a
 * variable this service does not declare is reported as undeclared, and its value is not read.
 */
@Path("/server-report")
@Produces(MediaType.APPLICATION_JSON)
public class CedarServerReportResource extends CedarMicroserviceResource {

  private final ServerName serverName;

  /**
   * The service's own application class, not this one. Asked for the code source, a class from the
   * shared library answers with the shared library's jar, which has the same timestamp on every
   * service and so tells a deploy check nothing.
   */
  private final Class<?> applicationClass;

  public CedarServerReportResource(CedarConfig cedarConfig, ServerName serverName, Class<?> applicationClass) {
    super(cedarConfig);
    this.serverName = serverName;
    this.applicationClass = applicationClass;
  }

  /** Logged in and holding MONITOR_READ, which is what every route below requires. */
  private void mustBeAllowedToReadTheServer() throws CedarException {
    CedarRequestContext c = buildRequestContext();
    c.must(c.user()).be(LoggedIn);
    c.must(c.user()).have(CedarPermission.MONITOR_READ);
  }

  /**
   * The environment variables this service resolved its configuration from: one entry per CEDAR
   * variable, saying whether this service declares it, whether the sandbox supplied a value, and
   * whether the host sets it at all. Secret values are masked; the value of a variable this service
   * does not declare is not reported.
   */
  @GET
  @Timed
  @Path("/environment")
  public Response environment() throws CedarException {
    mustBeAllowedToReadTheServer();

    SystemComponent component = SystemComponent.getFor(serverName);
    Map<String, String> sandbox = CedarConfig.getInstanceEnvironment();
    List<CedarEnvironmentReport.VariableEntry> variables =
        CedarEnvironmentReport.forComponent(component, sandbox);

    Map<String, Object> report = new LinkedHashMap<>();
    report.put("application", serverName.getDisplayName());
    report.put("server", serverName.getName());
    report.put("component", component == null ? null : component.getStringValue());
    report.put("variables", variables);
    return Response.ok(report).build();
  }

  /**
   * The configuration template with this service's environment substituted into it, secrets masked.
   * A placeholder that could not be resolved is left as the literal {@code ${NAME}} rather than
   * blanked, because that is the finding.
   */
  @GET
  @Timed
  @Path("/configuration")
  public Response configuration() throws CedarException {
    mustBeAllowedToReadTheServer();

    try {
      JsonNode resolved = CedarResolvedConfigurationReport.readMasked(CedarConfig.getInstanceEnvironment());
      Map<String, Object> report = new LinkedHashMap<>();
      report.put("application", serverName.getDisplayName());
      report.put("server", serverName.getName());
      report.put("configuration", resolved);
      return Response.ok(report).build();
    } catch (IOException e) {
      throw new CedarProcessingException("The resolved configuration could not be read", e);
    }
  }

  /**
   * The version the environment declares, with the path and modification time of the artifact the
   * JVM actually loaded. A service reporting the release version from an artifact older than the
   * release was pulled and not rebuilt.
   */
  @GET
  @Timed
  @Path("/build")
  public Response build() throws CedarException {
    mustBeAllowedToReadTheServer();

    Map<String, Object> report = new LinkedHashMap<>(
        CedarBuildInfo.forService(applicationClass, serverName.getDisplayName(), CedarConfig.getInstanceEnvironment()));
    report.put("server", serverName.getName());
    return Response.ok(report).build();
  }
}
