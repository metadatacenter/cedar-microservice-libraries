package org.metadatacenter.cedar.util.dw;

import ch.qos.logback.classic.Level;
import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jetty.ConnectorFactory;
import io.dropwizard.jetty.HttpConnectorFactory;
import io.dropwizard.server.DefaultServerFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ServerConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.AppLoggerQueueService;
import org.metadatacenter.server.logging.filter.ResponseLoggerFilter;
import org.metadatacenter.server.logging.filter.RequestIdGeneratorFilter;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.AuthorizationKeycloakAndApiKeyResolver;
import org.metadatacenter.server.security.IAuthorizationResolver;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;
import org.metadatacenter.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.DispatcherType;
import javax.servlet.FilterRegistration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import static org.eclipse.jetty.servlets.CrossOriginFilter.*;

public abstract class CedarMicroserviceApplication<T extends CedarMicroserviceConfiguration> extends Application<T> {

  private static final Logger log = LoggerFactory.getLogger(CedarMicroserviceApplication.class);
  private static final List<String> HTTP_HEADERS;
  private static final List<String> HTTP_METHODS;

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

    CedarRequestContextFactory.init(cedarConfig.getLinkedDataUtil());

    //Initialize user service
    CedarDataServices.initializeNeo4jServices(cedarConfig);
    userService = CedarDataServices.getNeoUserService();

    //Initialize Keycloak
    KeycloakDeploymentProvider keycloakDeploymentProvider = new KeycloakDeploymentProvider();
    keycloakDeploymentProvider.buildDeployment(cedarConfig.getKeycloakConfig());
    // Init Authorization Resolver
    IAuthorizationResolver authResolver = new AuthorizationKeycloakAndApiKeyResolver();
    Authorization.setAuthorizationResolver(authResolver);
    Authorization.setUserService(CedarDataServices.getNeoUserService());

    appLoggerQueueService = new AppLoggerQueueService(cedarConfig.getCacheConfig().getPersistent());
    AppLogger.initLoggerQueueService(appLoggerQueueService, SystemComponent.getFor(getServerName()));

    //Continue with the app
    initializeApp();

    DefaultServerFactory serverFactory = (DefaultServerFactory) configuration.getServerFactory();
    ((HttpConnectorFactory) serverFactory.getApplicationConnectors().get(0)).setPort(getApplicationHttpPort(configuration));
    ((HttpConnectorFactory) serverFactory.getAdminConnectors().get(0)).setPort(getApplicationAdminPort(configuration));
    System.setProperty("STOP.PORT", String.valueOf(getServerStopPort(configuration)));
    System.setProperty("STOP.KEY", "Stop:" + getServerName().getName() + ":Me");

    log.info("**************************************************************");
    log.info("********** Running CEDAR microservice " + getName());
    int httpPort = getHttpPort(configuration);
    log.info("********** HTTP  Port:" + httpPort);
    int adminPort = getAdminPort(configuration);
    log.info("********** Admin Port:" + adminPort);
    setupEnvironment(environment);
    runApp(configuration, environment);

    environment.jersey().register(CedarServerInsightReportResource.class);
    environment.jersey().register(RequestIdGeneratorFilter.class);
    environment.jersey().register(ResponseLoggerFilter.class);
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
    String httpOrigins = "*";
    String httpHeaders = StringUtils.join(HTTP_HEADERS, ",");
    String httpMethods = StringUtils.join(HTTP_METHODS, ",");
    log.info("Setting up CORS...");
    log.info(ALLOWED_ORIGINS_PARAM + ":" + httpOrigins);
    log.info(ALLOWED_HEADERS_PARAM + ":" + httpHeaders);
    log.info(ALLOWED_METHODS_PARAM + ":" + httpMethods);
    cors.setInitParameter(ALLOWED_ORIGINS_PARAM, httpOrigins);
    cors.setInitParameter(ALLOWED_HEADERS_PARAM, httpHeaders);
    cors.setInitParameter(ALLOWED_METHODS_PARAM, httpMethods);
    // Add URL mapping
    cors.addMappingForUrlPatterns(EnumSet.allOf(DispatcherType.class), true, "/*");
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
