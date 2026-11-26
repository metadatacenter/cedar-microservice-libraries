package org.metadatacenter.server.security;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.config.KeycloakConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Builds the realm verifier without Keycloak's retired Java adapter. */
public class KeycloakDeploymentProvider {
  private static final Logger log = LoggerFactory.getLogger(KeycloakDeploymentProvider.class);
  static final long CONNECTION_TIMEOUT_MILLIS = 2_000;
  static final long SOCKET_TIMEOUT_MILLIS = 5_000;

  public KeycloakTokenVerifier buildDeployment(KeycloakConfig config) {
    return buildDeployment(config, System::nanoTime);
  }

  KeycloakTokenVerifier buildDeployment(KeycloakConfig config, java.util.function.LongSupplier clock) {
    var tls = ClientTlsStrategyBuilder.create().useSystemProperties();
    if (config.isAllowInsecureTls()) {
      log.warn("Keycloak certificate and hostname verification are disabled by explicit development opt-in");
      tls.setSslContext(KeycloakUtils.buildInsecureSslContext(true))
          .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
          .setHostnameVerifier(NoopHostnameVerifier.INSTANCE);
    }
    var connections = PoolingHttpClientConnectionManagerBuilder.create().useSystemProperties()
        .setTlsSocketStrategy(tls.buildClassic()).setMaxConnTotal(4).setMaxConnPerRoute(4)
        .setDefaultConnectionConfig(ConnectionConfig.custom()
            .setConnectTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT_MILLIS))
            .setSocketTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT_MILLIS)).build()).build();
    var client = HttpClients.custom().useSystemProperties().setConnectionManager(connections)
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(CONNECTION_TIMEOUT_MILLIS))
            .setResponseTimeout(Timeout.ofMilliseconds(SOCKET_TIMEOUT_MILLIS)).build())
        .disableAutomaticRetries().disableRedirectHandling().disableCookieManagement().build();
    return new KeycloakTokenVerifier(config.getAuthServerUrl(), config.getRealm(), client, clock);
  }
}
