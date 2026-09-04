package org.metadatacenter.util.test;

import java.io.EOFException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The HTTP client the server tests share, and the one retry it needs.
 *
 * <p>A test class holds its client for its whole run, so successive requests reuse pooled
 * connections, and a connection the server has already closed can still be handed to the next one.
 * Two things close one: a server that answers without reading the request body, which is what an
 * authentication or authorization refusal does, and a connector's idle timeout, thirty seconds by
 * default. The client drops such a connection from its pool only once its selector thread has
 * processed the close. On a loaded machine that thread is slow enough for the next request to be
 * written first, into a socket the server has finished with, and no response bytes ever come back.
 *
 * <p>The JDK client recovers from this on its own for GET and HEAD alone. {@code
 * MultiExchange.canRetryRequest} treats every other method as unsafe to repeat unless {@code
 * jdk.httpclient.enableAllMethodRetry} is set, which it is not by default. So a POST or a PUT
 * surfaces an IOException about an empty response stream, which says nothing about the request
 * under test, and it surfaces one only under the load that widens the race.
 *
 * <p>One retry removes it. The failed send discards the dead connection, so a second attempt opens
 * a fresh one. The retry is deliberately narrow: it fires only when not a single response byte
 * arrived, which is what a connection closed before the request was read looks like from here. The
 * other way to reach that outcome — a server that read the request, did the work and then wrote
 * nothing — needs the connection to break between the resource method returning and its response
 * reaching the socket, which an in-process test server does not do to itself. A retried request is
 * therefore one the server never saw, so repeating it cannot repeat a side effect. A transport
 * failure of any other shape is reported as it happened.
 */
public final class TestHttpClient {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private TestHttpClient() {
  }

  /** Sends one request, retrying once if a pooled connection died before answering. */
  public static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    try {
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    } catch (IOException firstAttempt) {
      if (!answeredWithNoBytes(firstAttempt)) {
        throw firstAttempt;
      }
      return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    }
  }

  /**
   * Whether the send failed without receiving a single response byte, the signature of a connection
   * the server had already closed. The JDK reports it as an EOF, or as its header parser finding an
   * empty stream. Only the first has a dedicated exception type, so the message is the only handle
   * on the second. Wrapping is shallow but real, so the cause chain is walked.
   */
  private static boolean answeredWithNoBytes(Throwable failure) {
    Throwable cause = failure;
    for (int depth = 0; cause != null && depth < 8; depth++, cause = cause.getCause()) {
      if (cause instanceof EOFException) {
        return true;
      }
      String message = cause.getMessage();
      if (message != null && message.contains("received no bytes")) {
        return true;
      }
    }
    return false;
  }

}
