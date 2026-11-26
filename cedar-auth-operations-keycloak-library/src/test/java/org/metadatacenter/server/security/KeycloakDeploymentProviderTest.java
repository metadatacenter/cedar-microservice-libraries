package org.metadatacenter.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jakarta.ws.rs.ServiceUnavailableException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.util.JsonSerialization;
import org.metadatacenter.config.KeycloakConfig;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
class KeycloakDeploymentProviderTest {
  @Test
  void aSelfSignedRealmIsTrustedOnlyWithExplicitDevelopmentOptIn(@TempDir Path dir) throws Exception {
    var server = HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.setHttpsConfigurator(new HttpsConfigurator(KeycloakAdminClientTest.selfSigned(dir)));
    String base = "https://127.0.0.1:" + server.getAddress().getPort();
    String realm = base + "/realms/CEDAR";
    server.createContext("/realms/CEDAR/.well-known/openid-configuration", exchange -> {
      byte[] body = ("{\"issuer\":\"" + realm + "\",\"jwks_uri\":\"" + realm + "/certs\"}")
          .getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    var key = KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
    byte[] keys = JsonSerialization.writeValueAsBytes(Map.of("keys", List.of(
        JWKBuilder.create().kid("realm").algorithm("RS256").rsa(key, KeyUse.SIG))));
    server.createContext("/realms/CEDAR/certs", exchange -> {
      exchange.sendResponseHeaders(200, keys.length);
      try (var out = exchange.getResponseBody()) { out.write(keys); }
    });
    server.start();
    try {
      var provider = new KeycloakDeploymentProvider();
      try (var strict = provider.buildDeployment(config(base, false));
           var development = provider.buildDeployment(config(base, true))) {
        assertThrows(ServiceUnavailableException.class, strict::verifySigningKeys);
        assertDoesNotThrow(development::verifySigningKeys);
      }
    } finally {
      server.stop(0);
    }
  }

  private KeycloakConfig config(String base, boolean insecure) throws Exception {
    return new ObjectMapper().readValue("""
        {"realm":"CEDAR", "authServerUrl":"%s", "allowInsecureTls":%s}
        """.formatted(base, insecure), KeycloakConfig.class);
  }
}
