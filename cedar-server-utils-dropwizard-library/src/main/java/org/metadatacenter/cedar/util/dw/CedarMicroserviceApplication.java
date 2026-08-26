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
import org.eclipse.jetty.servlets.CrossOriginFilter;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

public abstract class CedarMicroserviceApplication<T extends CedarMicroserviceConfiguration> extends Application<T> {

  private static final Logger log = LoggerFactory.getLogger(CedarMicroserviceApplication.class);
  static final String CORS_ALLOWED_ORIGINS_ENV = "CEDAR_CORS_ALLOWED_ORIGINS";
  static final String DEFAULT_CORS_ALLOWED_ORIGINS = "*";
  private static final List<String> HTTP_HEADERS;
  private static final List<String> HTTP_METHODS;
  static final List<String> HTTP_EXPOSED_HEADERS;

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

    HTTP_EXPOSED_HEADERS = List.of(
        HttpHeaders.ETAG,
        CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS,
        HttpHeaders.CONTENT_DISPOSITION);
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

    bootstrap.addBundle(new AssetsBundle("/assets/swagger-api/swagger.json", "/swagger-api/swagger.json"));

    log.info("********** Initializing CEDAR Config for " + getName());
    // Initialize map with environment vars that this server expects
    SystemComponent systemComponent = SystemComponent.getFor(getServerName());
    Map<String, String> environmentSandbox = CedarEnvironmentVariableProvider.getFor(systemComponent);
    // Initialize config
    cedarConfig = CedarConfig.getInstance(environmentSandbox);

    initializeWithBootstrap(bootstrap, cedarConfig);
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
    ((HttpConnectorFactory) serverFactory.getApplicationConnectors().get(0)).setPort(getApplicationHttpPort(configuration));
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
    environment.jersey().register(RequestIdGeneratorFilter.class);
    environment.jersey().register(ResponseLoggerFilter.class);
    environment.jersey().register(new InstanceContextInjectionFeature(environment.jersey().getResourceConfig()));
  }

  private Integer getApplicationHttpPort(T configuration) {
    ServerConfig serverConfig = cedarConfig.getServers().get(getServerName());
    return configuration.getTestPort().orElse(serverConfig.getHttpPort());
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

    // Enable CORS headers
    final FilterRegistration.Dynamic cors = environment.servlets().addFilter("CORS", CrossOriginFilter.class);

    // Configure CORS parameters
    String httpOrigins = resolveCorsAllowedOrigins(System.getenv());
    String httpHeaders = String.join(",", HTTP_HEADERS);
    String httpMethods = String.join(",", HTTP_METHODS);
    String httpExposedHeaders = String.join(",", HTTP_EXPOSED_HEADERS);
    log.info("Setting up CORS...");
    log.info(ALLOWED_ORIGINS_PARAM + ":" + httpOrigins);
    log.info(ALLOWED_HEADERS_PARAM + ":" + httpHeaders);
    log.info(ALLOWED_METHODS_PARAM + ":" + httpMethods);
    log.info(EXPOSED_HEADERS_PARAM + ":" + httpExposedHeaders);
    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, httpOrigins);
    cors.setInitParameter(ALLOWED_HEADERS_PARAM, httpHeaders);
    cors.setInitParameter(ALLOWED_METHODS_PARAM, httpMethods);
    cors.setInitParameter(EXPOSED_HEADERS_PARAM, httpExposedHeaders);
    // Add URL mapping
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
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

  protected abstract void initializeApp();

  protected abstract void runApp(T configuration, Environment environment);

  protected abstract ServerName getServerName();

  protected abstract void initializeWithBootstrap(Bootstrap<T> bootstrap, CedarConfig cedarConfig);

  @Override
  public String getName() {
    return getServerName().getName();
  }

}
