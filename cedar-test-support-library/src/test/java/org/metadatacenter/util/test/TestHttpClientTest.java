package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The retry in {@link TestHttpClient} exists for a race — a pooled connection the server closes just
 * as the next request is written to it — which no test could lose reliably. A stub server scripts
 * the outcome of that race instead, one behaviour per connection, so both the recovery and its
 * limit are asserted deterministically.
 */
public class TestHttpClientTest {

  /** How the stub answers one connection. */
  private enum Behavior {
    /** Read the request, then close without writing: what a server closing a pooled connection does. */
    CLOSE_WITHOUT_ANSWERING,
    ANSWER_204,
    /** Answer with something that is not a status line, so the client rejects a real response. */
    ANSWER_GARBAGE
  }

  @Test
  public void aConnectionClosedBeforeAnsweringIsRetried() throws Exception {
    try (StubServer server = new StubServer(List.of(Behavior.CLOSE_WITHOUT_ANSWERING, Behavior.ANSWER_204))) {
      HttpResponse<String> response = TestHttpClient.send(requestWithABody(server.baseUrl()));

      Assertions.assertEquals(204, response.statusCode(),
          "the retry should have reached the server on a fresh connection");
      Assertions.assertEquals(2, server.connections(),
          "the send should have taken exactly one retry");
    }
  }

  @Test
  public void aServerThatAnsweredIsNotSentTheRequestTwice() throws Exception {
    try (StubServer server = new StubServer(List.of(Behavior.ANSWER_GARBAGE, Behavior.ANSWER_204))) {
      Assertions.assertThrows(IOException.class, () -> TestHttpClient.send(requestWithABody(server.baseUrl())));

      Assertions.assertEquals(1, server.connections(),
          "response bytes arrived, so the request reached the server and the failure must stand");
    }
  }

  /**
   * A request with a body and a method the JDK client will not retry for itself, which is the case
   * the retry was written for. Sending it twice also proves the request and its body publisher
   * survive resubscription.
   */
  private static HttpRequest requestWithABody(String baseUrl) {
    return HttpRequest.newBuilder(URI.create(baseUrl + "/command"))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
  }

  /** A server that answers each successive connection as the script says, and counts them. */
  private static final class StubServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final AtomicInteger connections = new AtomicInteger();

    StubServer(List<Behavior> script) throws IOException {
      serverSocket = new ServerSocket(0, script.size(), InetAddress.getLoopbackAddress());
      Thread thread = new Thread(() -> serve(script), "test-http-client-stub");
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
          switch (behavior) {
            case ANSWER_204 -> write(connection, "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n");
            case ANSWER_GARBAGE -> write(connection, "not-a-status-line\r\n\r\n");
            case CLOSE_WITHOUT_ANSWERING -> {
            }
          }
        } catch (IOException closed) {
          return;
        }
      }
    }

    private static void write(Socket connection, String response) throws IOException {
      connection.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
      connection.getOutputStream().flush();
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
