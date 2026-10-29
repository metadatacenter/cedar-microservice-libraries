package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.metadatacenter.config.ArtifactServiceConfig;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CedarHeaderParameters;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class ArtifactServiceClientTest {
  private static final String KEY = "test-only-artifact-service-key-not-for-prod";
  private HttpServer artifact;
  private HttpServer other;
  private String base;
  private ArtifactServiceClient client;
  private CedarRequestContext user;
  private final List<List<String>> requests = new CopyOnWriteArrayList<>();
  private final AtomicInteger otherCalls = new AtomicInteger();
  private int status = 200;
  private String redirect;

  @BeforeEach
  void start() throws Exception {
    other = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    other.createContext("/", exchange -> {
      otherCalls.incrementAndGet();
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    other.start();
    artifact = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    artifact.createContext("/", exchange -> {
      var headers = exchange.getRequestHeaders();
      requests.add(List.of(exchange.getRequestMethod(), headers.getFirst(ArtifactServiceConfig.HEADER),
          headers.getFirst("Authorization"), headers.getFirst(CedarHeaderParameters.GLOBAL_REQUEST_ID_KEY),
          String.valueOf(headers.getFirst("If-Match")),
          new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
      if (redirect != null) exchange.getResponseHeaders().set("Location", redirect);
      exchange.sendResponseHeaders(status, -1);
      exchange.close();
    });
    artifact.start();
    base = "http://127.0.0.1:" + artifact.getAddress().getPort() + "/";
    CedarConfig config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(config.getServers().getArtifact().getBase()).thenReturn(base);
    when(config.getArtifactService().requireApiKey()).thenReturn(KEY);
    client = new ArtifactServiceClient(config);
    user = mock(CedarRequestContext.class);
    when(user.getAuthorizationHeader()).thenReturn("Bearer original-user-token");
    when(user.getGlobalRequestIdHeader()).thenReturn("request-id");
    when(user.getIfMatchHeader()).thenReturn("\"3\"");
  }

  @AfterEach
  void stop() { artifact.stop(0); other.stop(0); }

  @Test
  void authenticatesAllVerbsWithoutReplacingUserOrRevision() throws Exception {
    client.get(base + "templates/id", user).close();
    client.post(base + "templates", user, "{\"name\":\"created\"}", HttpTimeouts.BATCH).close();
    client.put(base + "templates/id", user, "{\"name\":\"updated\"}").close();
    client.delete(base + "templates/id", user, "\"4\"").close();
    assertEquals(4, requests.size());
    for (var request : requests) {
      assertEquals(KEY, request.get(1));
      assertEquals("Bearer original-user-token", request.get(2));
      assertEquals("request-id", request.get(3));
    }
    assertEquals("{\"name\":\"created\"}", requests.get(1).get(5));
    assertEquals("\"3\"", requests.get(2).get(4));
    assertEquals("\"4\"", requests.get(3).get(4));
    verify(user, never()).getCedarUser();
  }

  @Test
  void refusesForeignDestinationsBeforeSendingAnything() {
    for (String url : List.of("http://127.0.0.1:" + other.getAddress().getPort() + "/templates",
        base.replace("127.0.0.1", "localhost") + "templates", base + "templates#fragment",
        base.replace("http://", "http://user@") + "templates")) {
      assertThrows(IllegalArgumentException.class, () -> client.get(url, user));
    }
    assertEquals(0, otherCalls.get());
    assertTrue(requests.isEmpty());
  }

  /**
   * A request attempted only once that finds no connection never reached the service. A GET, or a PUT
   * carrying If-Match, is repeated after an answerless attempt, so a refused last attempt does not show
   * that an earlier one went unreceived.
   */
  @Test
  void onlyARequestAttemptedOnceThatCouldNotConnectIsNeverSent() throws Exception {
    int closedPort;
    try (var socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
      closedPort = socket.getLocalPort();
    }
    String closed = "http://127.0.0.1:" + closedPort + "/";
    CedarConfig config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(config.getServers().getArtifact().getBase()).thenReturn(closed);
    when(config.getArtifactService().requireApiKey()).thenReturn(KEY);
    ArtifactServiceClient unreachable = new ArtifactServiceClient(config);

    assertThrows(ArtifactRequestNotSentException.class, () -> unreachable.post(closed + "templates", user, "{}"));
    assertThrows(ArtifactRequestNotSentException.class,
        () -> unreachable.post(closed + "templates", user, "{}", HttpTimeouts.BATCH));
    assertThrows(ArtifactRequestNotSentException.class, () -> unreachable.put(closed + "templates/id", user, "{}", null));
    assertThrows(ArtifactRequestNotSentException.class, () -> unreachable.delete(closed + "templates/id", user, null));

    for (Executable repeated : List.<Executable>of(() -> unreachable.get(closed + "templates/id", user),
        () -> unreachable.put(closed + "templates/id", user, "{}"),
        () -> unreachable.delete(closed + "templates/id", user, "\"4\""))) {
      var failure = assertThrows(CedarDependencyUnavailableException.class, repeated);
      assertFalse(failure instanceof ArtifactRequestNotSentException, failure.toString());
    }
  }

  @Test
  void neverFollowsRedirectsOrRetriesErrorResponsesInEitherPool() throws Exception {
    redirect = "http://127.0.0.1:" + other.getAddress().getPort() + "/steal-key";
    status = 307;
    assertEquals(307, client.get(base + "templates", user).getCode());
    assertEquals(307, client.get(base + "templates", user, HttpTimeouts.BATCH).getCode());
    assertEquals(0, otherCalls.get());
    redirect = null;
    status = 503;
    assertEquals(503, client.post(base + "templates", user, "{}").getCode());
    assertEquals(503, client.post(base + "templates", user, "{}", HttpTimeouts.BATCH).getCode());
    assertEquals(4, requests.size());
  }
}
