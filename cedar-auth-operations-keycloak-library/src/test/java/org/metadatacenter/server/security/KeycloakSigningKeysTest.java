package org.metadatacenter.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.crypto.KeyUse;
import org.keycloak.jose.jwk.JSONWebKeySet;
import org.keycloak.jose.jwk.JWK;
import org.keycloak.jose.jwk.JWKBuilder;
import org.keycloak.util.JsonSerialization;
import org.metadatacenter.config.KeycloakConfig;

import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A server can verify bearer tokens only while it can fetch the realm's signing keys. The probe passes when the
 * realm publishes one and fails, with the real cause, when it cannot be reached, answers too slowly, or publishes
 * nothing the verifier could use.
 */
@Tag("integration")
class KeycloakSigningKeysTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private HttpServer keycloak;
  private String base;
  private String discoveryBody;
  private String keySetBody;

  @BeforeEach
  void startKeycloak() throws Exception {
    keycloak = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    base = "http://127.0.0.1:" + keycloak.getAddress().getPort();
    String realm = base + "/realms/CEDAR";
    discoveryBody = """
        {"issuer": "%1$s", "jwks_uri": "%1$s/protocol/openid-connect/certs"}
        """.formatted(realm);
    keySetBody = keySet(JWKBuilder.create().kid("signing").algorithm("RS256").rsa(rsaPublicKey(), KeyUse.SIG));
    keycloak.createContext("/realms/CEDAR/.well-known/openid-configuration", exchange -> {
      byte[] body = discoveryBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    keycloak.createContext("/realms/CEDAR/protocol/openid-connect/certs", exchange -> {
      byte[] body = keySetBody.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    keycloak.start();
  }

  @AfterEach
  void stopKeycloak() {
    keycloak.stop(0);
  }

  @Test
  void aRealmThatPublishesASigningKeyPasses() throws Exception {
    assertDoesNotThrow(() -> KeycloakSigningKeys.verify(deployment(base)));
  }

  @Test
  void aKeySetWithNoSigningKeyFails() throws Exception {
    keySetBody = keySet(JWKBuilder.create().kid("encryption").algorithm("RSA-OAEP").rsa(rsaPublicKey(), KeyUse.ENC));

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> KeycloakSigningKeys.verify(deployment(base)));
    assertTrue(failure.getMessage().contains("/protocol/openid-connect/certs"), failure.getMessage());
  }

  @Test
  void aDiscoveryDocumentThatNamesNoKeySetFails() throws Exception {
    discoveryBody = """
        {"issuer": "%s/realms/CEDAR"}
        """.formatted(base);

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> KeycloakSigningKeys.verify(deployment(base)));
    assertTrue(failure.getMessage().contains("names no key set"), failure.getMessage());
  }

  @Test
  void anUnreachableKeycloakFailsWithItsCause() throws Exception {
    int closedPort;
    try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }

    Exception failure = assertThrows(Exception.class,
        () -> KeycloakSigningKeys.verify(deployment("http://127.0.0.1:" + closedPort)));
    assertInstanceOf(ConnectException.class, rootCause(failure), failure.toString());
  }

  /**
   * A Keycloak that accepts the connection and never answers is the case the adapter's read timeout exists for:
   * without it, every bearer request waited on such a server for as long as the operating system allowed.
   */
  @Test
  void aKeycloakThatNeverAnswersFailsWithinTheReadTimeout() throws Exception {
    try (ServerSocket silent = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
      List<Socket> held = new CopyOnWriteArrayList<>();
      Thread acceptor = new Thread(() -> {
        try {
          while (true) {
            held.add(silent.accept());
          }
        } catch (Exception closed) {
          // The socket closes when the test ends.
        }
      });
      acceptor.setDaemon(true);
      acceptor.start();

      long started = System.nanoTime();
      Exception failure = assertThrows(Exception.class,
          () -> KeycloakSigningKeys.verify(deployment("http://127.0.0.1:" + silent.getLocalPort())));
      long elapsedMillis = (System.nanoTime() - started) / 1_000_000;

      assertInstanceOf(SocketTimeoutException.class, rootCause(failure), failure.toString());
      assertTrue(elapsedMillis < KeycloakDeploymentProvider.SOCKET_TIMEOUT_MILLIS + 5_000,
          "the probe waited " + elapsedMillis + "ms");
      for (Socket socket : held) {
        socket.close();
      }
    }
  }

  private KeycloakDeployment deployment(String authServerUrl) throws Exception {
    return new KeycloakDeploymentProvider().buildDeployment(objectMapper.readValue("""
        {"realm": "CEDAR", "authServerUrl": "%s", "sslRequired": "external",
         "resource": "cedar-angular-app", "publicClient": true}
        """.formatted(authServerUrl), KeycloakConfig.class));
  }

  private static String keySet(JWK key) throws Exception {
    JSONWebKeySet keySet = new JSONWebKeySet();
    keySet.setKeys(new JWK[] {key});
    return JsonSerialization.writeValueAsString(keySet);
  }

  private static PublicKey rsaPublicKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair().getPublic();
  }

  private static Throwable rootCause(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root;
  }
}
