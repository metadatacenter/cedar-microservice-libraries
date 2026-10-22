package org.metadatacenter.util.test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * The credential grid the server suites use to show that a disabled, deleted or malformed credential
 * authenticates nobody. Its failure modes are the ones that matter: a credential that should be
 * refused but works, a fixture so broken that every route refuses everyone, and a matrix with nothing
 * in it to compare. The stub here is a server whose answers are known: it accepts one key, honours a
 * disabled key on one route only, and refuses everything on another.
 */
@Tag("integration")
public class CredentialMatrixTest {

  private static final String GOOD = "apiKey good";
  private static final String DISABLED = "apiKey disabled";

  private HttpServer server;
  private String baseUrl;

  @BeforeEach
  public void startStub() throws Exception {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", exchange -> {
      String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(answer(exchange, body), -1);
      exchange.close();
    });
    server.start();
    baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  public void stopStub() {
    server.stop(0);
  }

  private static int answer(HttpExchange exchange, String body) {
    String authorization = exchange.getRequestHeaders().getFirst("Authorization");
    String path = exchange.getRequestURI().getPath();
    if (path.equals("/broken")) {
      return 401;
    }
    if (path.equals("/leaky") && DISABLED.equals(authorization)) {
      return 200;
    }
    if (!GOOD.equals(authorization)) {
      return 401;
    }
    if (exchange.getRequestMethod().equals("POST")) {
      boolean json = "application/json".equals(exchange.getRequestHeaders().getFirst("Content-Type"));
      boolean traced = "trace-1".equals(exchange.getRequestHeaders().getFirst("X-Trace"));
      return json && traced && body.equals("{\"name\":\"n\"}") ? 201 : 400;
    }
    return 200;
  }

  @Test
  public void aGridWhoseEveryCellHoldsPasses() {
    CredentialMatrix matrix = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .refused("no credential", null)
        .refused("a disabled key", DISABLED);
    matrix.when("GET", "/things").accepting(200)
        .when("POST", "/things", "{\"name\":\"n\"}").header("X-Trace", "trace-1").accepting(201)
        .verify();
  }

  @Test
  public void aRefusedCredentialThatAuthenticatesFailsTheGrid() {
    CredentialMatrix matrix = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .refused("a disabled key", DISABLED);
    matrix.when("GET", "/things").accepting(200)
        .when("GET", "/leaky").accepting(200);

    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, matrix::verify);

    String message = failure.getMessage();
    Assertions.assertTrue(message.contains("GET /leaky with a disabled key: expected [401] but got 200"), message);
    Assertions.assertEquals(1, failure.getActual().getValue(), message);
  }

  /** A route that refuses everyone satisfies every refusal, so only the accepted credential exposes it. */
  @Test
  public void aRouteThatRefusesEveryoneFailsOnTheAcceptedCredential() {
    CredentialMatrix matrix = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .refused("no credential", null)
        .refused("a disabled key", DISABLED);
    matrix.when("GET", "/broken").accepting(200);

    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, matrix::verify);

    String message = failure.getMessage();
    Assertions.assertTrue(message.contains("GET /broken with the user's own key: expected [200] but got 401"), message);
    Assertions.assertEquals(1, failure.getActual().getValue(), message);
  }

  @Test
  public void aGridThatCouldPassVacuouslyFails() {
    CredentialMatrix noAccepted = new CredentialMatrix(baseUrl)
        .refused("no credential", null)
        .refused("a disabled key", DISABLED);
    noAccepted.when("GET", "/broken").accepting(200);
    Assertions.assertThrows(AssertionFailedError.class, noAccepted::verify,
        "without an accepted credential a grid of refusals passes against a route nobody can reach");

    CredentialMatrix nothingRefused = new CredentialMatrix(baseUrl).accepted("the user's own key", GOOD);
    nothingRefused.when("GET", "/things").accepting(200);
    Assertions.assertThrows(AssertionFailedError.class, nothingRefused::verify);

    CredentialMatrix noRows = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .refused("no credential", null);
    Assertions.assertThrows(AssertionFailedError.class, noRows::verify);

    CredentialMatrix undeclaredRow = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .refused("no credential", null);
    undeclaredRow.when("GET", "/things");
    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, undeclaredRow::verify);
    Assertions.assertTrue(failure.getMessage().contains("GET /things: no status declared for the accepted credential"),
        failure.getMessage());
  }

  @Test
  public void aSecondAcceptedCredentialOrALabelUsedTwiceIsRefused() {
    CredentialMatrix twoAccepted = new CredentialMatrix(baseUrl)
        .accepted("the user's own key", GOOD)
        .accepted("another key", "apiKey other")
        .refused("no credential", null);
    twoAccepted.when("GET", "/things").accepting(200);
    Assertions.assertThrows(AssertionFailedError.class, twoAccepted::verify);

    CredentialMatrix matrix = new CredentialMatrix(baseUrl).accepted("a key", GOOD);
    Assertions.assertThrows(IllegalArgumentException.class, () -> matrix.refused("a key", DISABLED));
    Assertions.assertThrows(IllegalArgumentException.class, () -> matrix.accepted("no header", null));
  }
}
