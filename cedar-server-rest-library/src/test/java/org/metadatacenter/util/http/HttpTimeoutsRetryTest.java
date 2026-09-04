package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The two outbound failures the retry strategy has to tell apart. Both leave a call site with
 * nothing useful, and only one of them can be settled by sending the request again.
 */
class HttpTimeoutsRetryTest {

  @Test
  void aPostIsNotRetriedWhenTheDependencyAnswersServiceUnavailable() throws Exception {
    AtomicInteger requestCount = new AtomicInteger();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/create", exchange -> {
      requestCount.incrementAndGet();
      exchange.getRequestBody().readAllBytes();
      exchange.sendResponseHeaders(503, -1);
      exchange.close();
    });
    server.start();

    try {
      HttpTimeouts timeouts = new HttpTimeouts(1_000, 1_000, 5_000, 1, 1);
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/create";

      ClassicHttpResponse response = timeouts.execute(
          Request.post(url).bodyString("{}", ContentType.APPLICATION_JSON));

      assertEquals(503, response.getCode());
      assertEquals(1, requestCount.get());
    } finally {
      server.stop(0);
    }
  }

  @Test
  void anIdempotentRequestIsRepeatedWhenAPooledConnectionAnsweredNothing() throws Exception {
    try (StubServer server = new StubServer(List.of(Behavior.CLOSE_WITHOUT_ANSWERING, Behavior.ANSWER_204))) {
      HttpTimeouts timeouts = new HttpTimeouts(1_000, 1_000, 5_000, 1, 1);

      ClassicHttpResponse response = timeouts.execute(Request.get(server.baseUrl() + "/artifact"));

      assertEquals(204, response.getCode(),
          "the repeat should have reached the dependency on a fresh connection");
      assertEquals(2, server.connections(), "the call should have taken exactly one repeat");
    }
  }

  @Test
  void aPostIsNotRepeatedWhenAPooledConnectionAnsweredNothing() throws Exception {
    try (StubServer server = new StubServer(List.of(Behavior.CLOSE_WITHOUT_ANSWERING, Behavior.ANSWER_204))) {
      HttpTimeouts timeouts = new HttpTimeouts(1_000, 1_000, 5_000, 1, 1);

      assertThrows(NoHttpResponseException.class, () -> timeouts.execute(
          Request.post(server.baseUrl() + "/artifact").bodyString("{}", ContentType.APPLICATION_JSON)));

      assertEquals(1, server.connections(),
          "a create the dependency may already have made is not sent a second time");
    }
  }

  /** How the stub answers one connection. */
  private enum Behavior {
    /** Read the request, then close without writing: what a dependency retiring a connection does. */
    CLOSE_WITHOUT_ANSWERING,
    ANSWER_204
  }

  /** A server that answers each successive connection as the script says, and counts them. */
  private static final class StubServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final AtomicInteger connections = new AtomicInteger();

    StubServer(List<Behavior> script) throws IOException {
      serverSocket = new ServerSocket(0, script.size(), InetAddress.getLoopbackAddress());
      Thread thread = new Thread(() -> serve(script), "http-timeouts-stub");
      thread.setDaemon(true);
      thread.start();
    }

    private void serve(List<Behavior> script) {
      for (Behavior behavior : script) {
        try (Socket connection = serverSocket.accept()) {
          connections.incrementAndGet();
          // Draining the request first means the client finished writing, so the close that
          // follows is an orderly one and the client reads an empty stream rather than a reset.
          readOneRequest(connection.getInputStream());
          if (behavior == Behavior.ANSWER_204) {
            connection.getOutputStream().write(
                "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            connection.getOutputStream().flush();
          }
        } catch (IOException closed) {
          return;
        }
      }
    }

    /** Consumes exactly one request: the header block, then as many body bytes as it declares. */
    private static void readOneRequest(InputStream in) throws IOException {
      int contentLength = 0;
      StringBuilder line = new StringBuilder();
      int next;
      while ((next = in.read()) != -1) {
        if (next == '\n') {
          String header = line.toString();
          if (header.isEmpty()) {
            break;
          }
          if (header.toLowerCase(Locale.ROOT).startsWith("content-length:")) {
            contentLength = Integer.parseInt(header.substring(header.indexOf(':') + 1).trim());
          }
          line.setLength(0);
        } else if (next != '\r') {
          line.append((char) next);
        }
      }
      in.readNBytes(contentLength);
    }

    String baseUrl() {
      return "http://" + serverSocket.getInetAddress().getHostAddress() + ":" + serverSocket.getLocalPort();
    }

    int connections() {
      return connections.get();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }

}
