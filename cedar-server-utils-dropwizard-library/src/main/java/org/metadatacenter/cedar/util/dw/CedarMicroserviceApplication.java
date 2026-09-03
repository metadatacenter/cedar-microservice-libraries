package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import com.fasterxml.jackson.databind.DeserializationFeature;
import io.dropwizard.core.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.core.server.DefaultServerFactory;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import org.eclipse.jetty.ee10.servlets.CrossOriginFilter;
import org.eclipse.jetty.http.UriCompliance;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ServerConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.AppLoggerQueueService;
import org.metadatacenter.server.logging.filter.ResponseLoggerFilter;
import org.metadatacenter.server.logging.filter.RequestIdGeneratorFilter;
import org.metadatacenter.server.security.Authorization;
import org.keycloak.adapters.KeycloakDeployment;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;
import org.metadatacenter.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.ws.rs.core.HttpHeaders;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.eclipse.jetty.ee10.servlets.CrossOriginFilter.*;

public abstract class CedarMicroserviceApplication<T extends CedarMicroserviceConfiguration> extends Application<T> {

  private static final Logger log = LoggerFactory.getLogger(CedarMicroserviceApplication.class);
  static final String CORS_ALLOWED_ORIGINS_ENV = "CEDAR_CORS_ALLOWED_ORIGINS";
  static final String DEFAULT_CORS_ALLOWED_ORIGINS = "*";
  private static final List<String> HTTP_HEADERS;
  private static final List<String> HTTP_METHODS;
  static final List<String> HTTP_EXPOSED_HEADERS;

  /**
   * The generated OpenAPI document on the classpath, where a service ships one, and the path it is
   * served at. Four of the services build a spec into their jar; the rest have none, and for them
   * there is nothing to serve and nothing to advertise.
   */
  static final String API_SPEC_ASSET = "/assets/swagger-api/swagger.json";
  static final String API_SPEC_PATH = "/swagger-api/swagger.json";

  protected static CedarConfig cedarConfig;
  protected static UserService userService;
  protected static AppLoggerQueueService appLoggerQueueService;

  static {
    HTTP_HEADERS = new ArrayList<>();
    HTTP_HEADERS.add("X-Requested-With");
    HTTP_HEADERS.add("Content-Type");
    HTTP_HEADERS.add("Accept");
    HTTP_HEADERS.add("Origin");
    HTTP_HEADERS.add("Referer");
    HTTP_HEADERS.add("User-Agent");
    HTTP_HEADERS.add("Authorization");
    HTTP_HEADERS.add(HttpHeaders.IF_MATCH);

    HTTP_EXPOSED_HEADERS = CustomHttpConstants.EXPOSED_HEADERS;
    HTTP_HEADERS.add(CedarHeaderParameters.DEBUG);
    HTTP_HEADERS.add(CedarHeaderParameters.CLIENT_SESSION_ID);

    HTTP_METHODS = new ArrayList<>();
    HTTP_METHODS.add("OPTIONS");
    HTTP_METHODS.add("GET");
    HTTP_METHODS.add("PUT");
    HTTP_METHODS.add("POST");
    HTTP_METHODS.add("DELETE");
    HTTP_METHODS.add("HEAD");
    HTTP_METHODS.add("PATCH");
  }

  @Override
  public void initialize(Bootstrap<T> bootstrap) {
    // Enable variable substitution with environment variables
    bootstrap.setConfigurationSourceProvider(
        new SubstitutingSourceProvider(bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor())
    );

    // Only where there is a spec to serve. Registered unconditionally, the bundle answered 404 on
    // the ten services that ship no document, while the index resource advertised the link anyway.
    if (shipsApiSpec()) {
      bootstrap.addBundle(new AssetsBundle(API_SPEC_ASSET, API_SPEC_PATH));
    }

    log.info("********** Initializing CEDAR Config for " + getName());
    // Initialize map with environment vars that this server expects
    SystemComponent systemComponent = SystemComponent.getFor(getServerName());
    Map<String, String> environmentSandbox = CedarEnvironmentVariableProvider.getFor(systemComponent);
    // Initialize config
    cedarConfig = CedarConfig.getInstance(environmentSandbox);

    initializeWithBootstrap(bootstrap, cedarConfig);
  }

  /**
   * Whether this service ships an API spec.
   *
   * <p>Read from the classpath rather than from configuration: the document is built into the jar,
   * so its presence is the fact, and a flag beside it could only ever disagree with it. An earlier
   * {@code apiDoc} setting did exactly that — it was set on three services, missed a fourth that had
   * a spec, and was read by nothing.
   */
  static boolean shipsApiSpec() {
    return shipsApiSpec(API_SPEC_ASSET);
  }

  static boolean shipsApiSpec(String classpathLocation) {
    return CedarMicroserviceApplication.class.getResource(classpathLocation) != null;
  }

  @Override
  protected Level bootstrapLogLevel() {
    return Level.WARN;
  }

  @Override
  public void run(T configuration, Environment environment) throws Exception {
    log.info("********** Initializing CEDAR microservice " + getName());

    // Dropwizard 2 ignores unknown request-body properties by default; CEDAR's API contract
    // predates that change and rejects them, so restore the strict behavior.
    environment.getObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    CedarRequestContextFactory.init(cedarConfig.getLinkedDataUtil());

    //Initialize user service
    CedarDataServices.initializeNeo4jServices(cedarConfig);
    userService = CedarDataServices.getInstance().getNeoUserService();

    //Initialize Keycloak
    KeycloakDeploymentProvider keycloakDeploymentProvider = new KeycloakDeploymentProvider();
    KeycloakDeployment keycloakDeployment = keycloakDeploymentProvider.buildDeployment(cedarConfig.getKeycloakConfig());
    // Init Authorization Resolver. The deployment carries the realm's signing keys, so the resolver can
    // verify a bearer token's signature instead of trusting its payload.
    IAuthorizationResolver authResolver = new AuthorizationKeycloakAndApiKeyResolver(keycloakDeployment);
    Authorization.setAuthorizationResolver(authResolver);
    Authorization.setUserService(CedarDataServices.getInstance().getNeoUserService());

    appLoggerQueueService = new AppLoggerQueueService(cedarConfig.getCacheConfig().getPersistent());
    AppLogger.initLoggerQueueService(appLoggerQueueService, SystemComponent.getFor(getServerName()));

    //Continue with the app
    initializeApp();

    DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
    HttpConnectorFactory applicationConnector =
        (HttpConnectorFactory) serverFactory.getApplicationConnectors().get(0);
    applicationConnector.setPort(getApplicationHttpPort(configuration));
    // Artifact identifiers are IRIs carried as one percent-encoded path parameter, so their
    // encoded "https://" contains %2F by design. Jetty 12 rejects encoded separators by default;
    // allow precisely that case without enabling its broader Jetty 11 or legacy URI modes.
    applicationConnector.setUriCompliance(cedarUriCompliance());
    // Jetty's EE10 Servlet layer has its own guard for ambiguous paths. It must permit decoding
    // before Jersey can expose the encoded IRI as a path parameter; the connector-level mode above
    // remains the security boundary and admits only encoded separators.
    environment.getApplicationContext().getServletHandler().setDecodeAmbiguousURIs(true);
    HttpConnectorFactory adminConnector = (HttpConnectorFactory) serverFactory.getAdminConnectors().get(0);
    adminConnector.setPort(getApplicationAdminPort(configuration));
    // The admin connector answers /metrics and /threads to anyone who reaches it — no credentials, a
    // few hundred kilobytes each. Dropwizard binds every interface unless told otherwise, so it was
    // reachable from the network rather than only from the host. Loopback is enough for everything
    // that reads it: cedar-services.sh polls 127.0.0.1, and the container health check curls localhost
    // from inside the container.
    adminConnector.setBindHost("127.0.0.1");
    System.setProperty("STOP.PORT", String.valueOf(getServerStopPort(configuration)));
    // A stop key anyone can derive from the service name is not a key. Nothing drives this connector —
    // cedar-services.sh stops a service by signalling its pid — so a value that exists only in this
    // process is enough, and leaves no shutdown that can be triggered from outside it.
    System.setProperty("STOP.KEY", UUID.randomUUID().toString());

    log.info("**************************************************************");
    log.info("********** Running CEDAR microservice " + getName());
    int httpPort = getHttpPort(configuration);
    log.info("********** HTTP  Port:" + httpPort);
    int adminPort = getAdminPort(configuration);
    log.info("********** Admin Port:" + adminPort);
    setupEnvironment(environment);
    runApp(configuration, environment);

    environment.jersey().register(new CedarServerInsightReportResource(cedarConfig));
    // getClass() is the concrete application, so the build report names this service's own
    // artifact rather than the shared library every service loads.
    environment.jersey().register(new CedarServerReportResource(cedarConfig, getServerName(), getClass()));
    environment.jersey().register(new CedarHealthCheckResource(cedarConfig, environment.healthChecks()));
    environment.jersey().register(RequestIdGeneratorFilter.class);
    environment.jersey().register(ResponseLoggerFilter.class);
    environment.jersey().register(new InstanceContextInjectionFeature(environment.jersey().getResourceConfig()));
  }

  private Integer getApplicationHttpPort(T configuration) {
    ServerConfig serverConfig = cedarConfig.getServers().get(getServerName());
    return configuration.getTestPort().orElse(serverConfig.getHttpPort());
  }

  static UriCompliance cedarUriCompliance() {
    return UriCompliance.DEFAULT.with(
        "CEDAR_ARTIFACT_IRI_PATHS", UriCompliance.Violation.AMBIGUOUS_PATH_SEPARATOR);
  }

  private Integer getApplicationAdminPort(T configuration) {
    return cedarConfig.getServers().get(getServerName()).getAdminPort();
  }

  private Integer getServerStopPort(T configuration) {
    return cedarConfig.getServers().get(getServerName()).getStopPort();
  }

  private int getHttpPort(T configuration) {
    int httpPort = 0;
    DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
    for (ConnectorFactory connector : serverFactory.getApplicationConnectors()) {
      if (connector.getClass().isAssignableFrom(HttpConnectorFactory.class)) {
        httpPort = ((HttpConnectorFactory) connector).getPort();
        break;
      }
    }
    return httpPort;
  }

  private int getAdminPort(T configuration) {
    int httpPort = 0;
    DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
    for (ConnectorFactory connector : serverFactory.getAdminConnectors()) {
      if (connector.getClass().isAssignableFrom(HttpConnectorFactory.class)) {
        httpPort = ((HttpConnectorFactory) connector).getPort();
        break;
      }
    }
    return httpPort;
  }

  protected void setupEnvironment(Environment environment) {
    // Register Exception Mapper
    environment.jersey().register(new CedarCedarExceptionMapper());
    environment.jersey().register(new CedarExceptionMapper());

    registerSharedHealthChecks(environment);

    // Enable CORS headers
    final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

    // Configure CORS parameters
    log.info("Setting up CORS...");
    // The sandbox, not System.getenv. CEDAR_CORS_ALLOWED_ORIGINS is declared by every microservice, so
    // it arrives here the way every other setting does; reading the process environment directly meant
    // the one variable governing which origins may call CEDAR was invisible to the environment report.
    corsInitParameters(CedarConfig.getInstanceEnvironment()).forEach((name, value) -> {
      log.info(name + ":" + value);
      cors.setInitParameter(name, value);
    });
    // Add URL mapping
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
  }

  /**
   * The two dependencies every CEDAR microservice opens, probed here so that no server can publish
   * a health endpoint that stays green while they are gone.
   *
   * <p>They are gated differently because they fail differently. Neo4j resolves the caller of every
   * authenticated request through {@link Authorization}, so a server that cannot reach it can serve
   * nothing and is unhealthy. Redis carries the application log queue, whose enqueue is best-effort
   * by design and drops events rather than failing the request that produced them, so its loss is
   * reported and the server stays healthy. A service for which Redis carries something it cannot
   * drop registers its own gating check on that queue.
   *
   * <p>Both handles already exist at this point: {@code run} initializes the Neo4j services and the
   * logger queue before calling this. A subclass that overrides {@code setupEnvironment} must call
   * {@code super}, as {@link CedarMicroserviceApplicationWithMongo} does when it adds the document
   * store.
   */
  private void registerSharedHealthChecks(Environment environment) {
    environment.healthChecks().register("neo4j", CedarDependencyHealthCheck.gating(
        "Neo4j", CedarDataServices.getInstance().getProxies()::verifyConnectivity));
    environment.healthChecks().register("app-log-queue", CedarDependencyHealthCheck.reporting(
        "The Redis application log queue", appLoggerQueueService::verifyConnectivity));
  }

  static String resolveCorsAllowedOrigins(Map<String, String> environment) {
    String configuredOrigins = environment.get(CORS_ALLOWED_ORIGINS_ENV);
    if (configuredOrigins == null || configuredOrigins.isBlank()) {
      return DEFAULT_CORS_ALLOWED_ORIGINS;
    }
    String normalizedOrigins = Arrays.stream(configuredOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .collect(Collectors.joining(","));
    return normalizedOrigins.isEmpty() ? DEFAULT_CORS_ALLOWED_ORIGINS : normalizedOrigins;
  }

  static Map<String, String> corsInitParameters(Map<String, String> environment) {
    String allowedOrigins = resolveCorsAllowedOrigins(environment);
    Map<String, String> parameters = new LinkedHashMap<>();
    parameters.put(ALLOWED_ORIGINS_PARAM, allowedOrigins);
    parameters.put(ALLOWED_HEADERS_PARAM, String.join(",", HTTP_HEADERS));
    parameters.put(ALLOWED_METHODS_PARAM, String.join(",", HTTP_METHODS));
    parameters.put(EXPOSED_HEADERS_PARAM, String.join(",", HTTP_EXPOSED_HEADERS));
    parameters.put(ALLOW_CREDENTIALS_PARAM, Boolean.toString(corsAllowsCredentials(allowedOrigins)));
    return parameters;
  }

  static boolean corsAllowsCredentials(String allowedOrigins) {
    return Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .noneMatch(DEFAULT_CORS_ALLOWED_ORIGINS::equals);
  }

  protected abstract void initializeApp();

  protected abstract void runApp(T configuration, Environment environment);

  protected abstract ServerName getServerName();

  protected abstract void initializeWithBootstrap(Bootstrap<T> bootstrap, CedarConfig cedarConfig);

  @Override
  public String getName() {
    return getServerName().getName();
  }

}
