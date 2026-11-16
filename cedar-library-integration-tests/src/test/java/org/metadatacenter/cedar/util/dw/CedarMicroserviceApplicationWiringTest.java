package org.metadatacenter.cedar.util.dw;

import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What CedarMicroserviceApplication.run adds to every server, booted once here without a server's own
 * resources: the strict request-body policy, the shared health checks, the exception mappers and the
 * CORS filter. Each server's suite boots it as a side effect; this is its own test.
 */
@Tag("integration")
class CedarMicroserviceApplicationWiringTest {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();
  private static DropwizardTestSupport<CedarMicroserviceConfiguration> server;

  /** A server with no resources of its own beyond one that echoes a request body. */
  public static class WiringApplication extends CedarMicroserviceApplication<CedarMicroserviceConfiguration> {
    @Override
    protected void initializeApp() {
    }

    @Override
    protected void runApp(CedarMicroserviceConfiguration configuration, Environment environment) {
      environment.jersey().register(new EchoResource());
    }

    @Override
    protected ServerName getServerName() {
      return ServerName.GROUP;
    }

    @Override
    protected void initializeWithBootstrap(Bootstrap<CedarMicroserviceConfiguration> bootstrap, CedarConfig cedarConfig) {
    }
  }

  public record Probe(String name) {
  }

  @Path("/wiring-echo")
  public static class EchoResource {
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Probe echo(Probe probe) {
      return probe;
    }
  }

  @BeforeAll
  static void boot() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
        "CEDAR_GROUP_HTTP_PORT", "0",
        "CEDAR_GROUP_ADMIN_PORT", "0",
        "CEDAR_GROUP_STOP_PORT", "0",
        "CEDAR_REDIS_PERSISTENT_PORT", "1",
        "CEDAR_CORS_ALLOWED_ORIGINS", "https://allowed.example"));
    java.nio.file.Path config = Files.createTempFile("cedar-wiring", ".yml");
    Files.writeString(config, """
        server:
          applicationConnectors:
            - type: http
              port: 0
          adminConnectors:
            - type: http
              port: 0
        logging:
          level: WARN
        """);
    server = new DropwizardTestSupport<>(WiringApplication.class, config.toString());
    server.before();
  }

  @AfterAll
  static void stop() {
    if (server != null) {
      server.after();
    }
  }

  @Test
  void aRequestBodyWithAnUnknownPropertyIsRefused() throws Exception {
    HttpResponse<String> known = post("{\"name\":\"probe\"}");
    HttpResponse<String> unknown = post("{\"name\":\"probe\",\"unexpected\":true}");

    assertEquals(200, known.statusCode(), known.body());
    assertEquals(400, unknown.statusCode(), "the strict body policy is not applied: " + unknown.body());
  }

  @Test
  void theSharedHealthChecksAreRegistered() {
    Set<String> names = server.getEnvironment().healthChecks().getNames();

    assertTrue(names.containsAll(Set.of("neo4j", "keycloak-signing-keys", "app-log-queue")), names.toString());
  }

  @Test
  void aConfiguredOriginReceivesTheCorsHeaders() throws Exception {
    HttpResponse<String> allowed = CLIENT.send(HttpRequest.newBuilder(uri())
        .header("Origin", "https://allowed.example")
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"probe\"}")).build(), HttpResponse.BodyHandlers.ofString());

    assertEquals("https://allowed.example", allowed.headers().firstValue("Access-Control-Allow-Origin").orElse(null));
  }

  private static HttpResponse<String> post(String body) throws Exception {
    return CLIENT.send(HttpRequest.newBuilder(uri())
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static URI uri() {
    return URI.create("http://127.0.0.1:" + server.getLocalPort() + "/wiring-echo");
  }
}
