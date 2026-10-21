package org.metadatacenter.server.security;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jakarta.ws.rs.ProcessingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The administrator's Keycloak client signs in to the realm with the administrator's password and reads Keycloak's
 * responses tolerantly, so a property a newer Keycloak adds does not break it. It trusts the platform's certificate
 * authorities unless the development opt-in disables certificate verification.
 */
@Tag("integration")
class KeycloakAdminClientTest {

  private static final String PASSWORD = "store-password";

  private HttpServer server;
  private final List<String> grants = new CopyOnWriteArrayList<>();

  @AfterEach
  void stopKeycloak() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void theAdministratorSignsInWithItsPassword() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    serveTokens();

    try (Keycloak keycloak = KeycloakUtils.buildKeycloak(info("http", false))) {
      AccessTokenResponse response = keycloak.tokenManager().getAccessToken();

      assertEquals("access-1", response.getToken());
      assertEquals("refresh-1", response.getRefreshToken(), "a property after an unknown object is still read");
      assertEquals(300, response.getExpiresIn());
    }
    assertEquals(1, grants.size());
    String grant = grants.get(0);
    for (String parameter : List.of("grant_type=password", "username=cedar-admin", "password=admin-password",
        "client_id=admin-cli")) {
      assertTrue(grant.contains(parameter), grant);
    }
  }

  @Test
  void aSelfSignedKeycloakIsReachedOnlyWithTheDevelopmentOptIn(@TempDir Path dir) throws Exception {
    HttpsServer https = HttpsServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    https.setHttpsConfigurator(new HttpsConfigurator(selfSigned(dir)));
    server = https;
    serveTokens();

    try (Keycloak verifying = KeycloakUtils.buildKeycloak(info("https", false))) {
      assertThrows(ProcessingException.class, () -> verifying.tokenManager().getAccessToken(),
          "a certificate no trusted authority signed is refused");
    }
    try (Keycloak trusting = KeycloakUtils.buildKeycloak(info("https", true))) {
      assertEquals("access-1", trusting.tokenManager().getAccessToken().getToken());
    }
  }

  @Test
  void aUserIsReadWholeWhenKeycloakDescribesItWithAPropertyTheClientDoesNotKnow() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/admin/realms/CEDAR/users", exchange -> {
      byte[] body = """
          [{"id": "u-1", "future_detail": {"nested": {"depth": 2}, "list": [1]}, "username": "ada",
            "email": "ada@example.org"}]
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    serveTokens();

    try (Keycloak keycloak = KeycloakUtils.buildKeycloak(info("http", false))) {
      List<UserRepresentation> users = keycloak.realm("CEDAR").users().list();

      assertEquals(1, users.size());
      assertEquals("u-1", users.get(0).getId());
      assertEquals("ada", users.get(0).getUsername());
      assertEquals("ada@example.org", users.get(0).getEmail());
    }
  }

  /** Answers a password grant on the realm's token endpoint, with a property no Keycloak release defines. */
  private void serveTokens() {
    server.createContext("/realms/CEDAR/protocol/openid-connect/token", exchange -> {
      try (InputStream in = exchange.getRequestBody()) {
        grants.add(URLDecoder.decode(new String(in.readAllBytes(), StandardCharsets.UTF_8), StandardCharsets.UTF_8));
      }
      byte[] body = """
          {"access_token": "access-1", "token_type": "Bearer", "future_detail": {"nested": {"depth": 2}, "list": [1]},
           "refresh_token": "refresh-1", "expires_in": 300}
          """.getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    server.start();
  }

  private KeycloakUtilInfo info(String scheme, boolean allowInsecureTls) {
    KeycloakUtilInfo info = new KeycloakUtilInfo();
    info.setKeycloakBaseURI(scheme + "://127.0.0.1:" + server.getAddress().getPort());
    info.setKeycloakRealmName("CEDAR");
    info.setKeycloakClientId("admin-cli");
    info.setCedarAdminUserName("cedar-admin");
    info.setCedarAdminUserPassword("admin-password");
    info.setAllowInsecureTls(allowInsecureTls);
    return info;
  }

  /** A server context whose certificate, for 127.0.0.1, signs itself. */
  private static SSLContext selfSigned(Path dir) throws Exception {
    Path keystore = dir.resolve("keycloak.p12");
    Process keytool = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
        "-genkeypair", "-alias", "keycloak", "-keyalg", "RSA", "-keysize", "2048", "-validity", "1",
        "-dname", "CN=localhost", "-ext", "SAN=ip:127.0.0.1,dns:localhost", "-storetype", "PKCS12",
        "-keystore", keystore.toString(), "-storepass", PASSWORD, "-keypass", PASSWORD)
        .redirectErrorStream(true).start();
    String output = new String(keytool.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, keytool.waitFor(), output);
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream in = java.nio.file.Files.newInputStream(keystore)) {
      store.load(in, PASSWORD.toCharArray());
    }
    KeyManagerFactory keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
    keys.init(store, PASSWORD.toCharArray());
    SSLContext context = SSLContext.getInstance("TLS");
    context.init(keys.getKeyManagers(), null, null);
    return context;
  }
}
