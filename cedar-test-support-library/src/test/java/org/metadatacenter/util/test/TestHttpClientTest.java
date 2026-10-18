package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The server consumes a write before failing its response. A successful second response is available
 * to expose accidental replay: send must report the first failure, not turn it into a passing test.
 */
public class TestHttpClientTest {

  /** How the stub answers one connection. */
  private enum Behavior {
    /** The request was consumed, but its acknowledgement was lost. */
    CLOSE_WITHOUT_ANSWERING,
    ANSWER_204,
    ANSWER_PARTIAL_HEADERS,
    ANSWER_PARTIAL_BODY,
    /** Answer with something that is not a status line, so the client rejects a real response. */
    ANSWER_GARBAGE
  }

  @Test
  public void aCompleteResponseIsReturned() throws Exception {
    try (StubServer server = new StubServer(List.of(Behavior.ANSWER_204))) {
      HttpResponse<String> response = TestHttpClient.send(requestWithABody(server.baseUrl()));

      Assertions.assertEquals(204, response.statusCode());
      Assertions.assertEquals(1, server.requests());
    }
  }

  @ParameterizedTest
  @EnumSource(value = Behavior.class, names = "ANSWER_204", mode = EnumSource.Mode.EXCLUDE)
  public void aFailedResponseDoesNotReplayTheWrite(Behavior response) throws Exception {
    try (StubServer server = new StubServer(List.of(response, Behavior.ANSWER_204))) {
      Assertions.assertThrows(IOException.class, () -> TestHttpClient.send(requestWithABody(server.baseUrl())));

      Assertions.assertEquals(1, server.requests(),
          "the server applied the write; a lost or broken response must not cause another write");
    }
  }

  /**
   * Each stub uses a new port, so this POST uses a fresh connection. The lost-acknowledgement case
   * also guards the parent's JDK retry setting: enabling retries for all methods repeats the write.
   */
  private static HttpRequest requestWithABody(String baseUrl) {
    return HttpRequest.newBuilder(URI.create(baseUrl + "/command"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(5))
        .POST(HttpRequest.BodyPublishers.ofString("{}"))
        .build();
  }

  /** A server that answers each successive connection as the script says, and counts them. */
  private static final class StubServer implements AutoCloseable {

    private final ServerSocket serverSocket;
    private final AtomicInteger requests = new AtomicInteger();

    StubServer(List<Behavior> script) throws IOException {
      serverSocket = new ServerSocket(
          0, script.size(), InetAddress.getByName("127.0.0.1"));
      Thread thread = new Thread(() -> serve(script), "test-http-client-stub");
      thread.setDaemon(true);
      thread.start();
    }

    private void serve(List<Behavior> script) {
      for (Behavior behavior : script) {
        try (Socket connection = serverSocket.accept()) {
          // Draining the request first means the client finished writing, so the close that
          // follows is an orderly one and the client reads an empty stream rather than a reset.
          readOneRequest(connection.getInputStream());
          requests.incrementAndGet();
          switch (behavior) {
            case ANSWER_204 -> write(connection, "HTTP/1.1 204 No Content\r\nContent-Length: 0\r\n\r\n");
            case ANSWER_PARTIAL_HEADERS -> write(connection, "HTTP/1.1 200 OK\r\nContent-Length:");
            case ANSWER_PARTIAL_BODY -> write(connection,
                "HTTP/1.1 200 OK\r\nContent-Length: 20\r\nConnection: close\r\n\r\nx");
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

    int requests() {
      return requests.get();
    }

    @Override
    public void close() throws IOException {
      serverSocket.close();
    }
  }

}
