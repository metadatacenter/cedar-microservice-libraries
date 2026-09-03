package org.metadatacenter.util.test;

import java.io.EOFException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The HTTP client the route and permission probes share, and the one retry it needs.
 *
 * <p>A probe helper holds its client for a whole test class, so successive probes reuse pooled
 * connections. A server that answers a request without reading its body — which is what an
 * authentication or authorization refusal does — may then close that connection, and the client can
 * hand it to the next request before noticing. That request is written to a socket the server is
 * tearing down, and no response bytes ever come back. The JDK client recovers from this on its own
 * for idempotent requests only, because {@code jdk.httpclient.enableAllMethodRetry} defaults to
 * false. So a GET probe never sees the failure, and a POST or PUT probe reports an IOException that
 * says nothing about the route under test.
 *
 * <p>One retry removes it: the failed send discards the dead connection, so a second attempt opens
 * a fresh one. The retry is deliberately narrow. It fires only when no response bytes arrived,
 * which is the case where the server cannot have answered, and both probe helpers send requests
 * they expect to be refused before those requests take effect. A transport failure of any other
 * shape is reported as it happened.
 */
final class ProbeClient {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private ProbeClient() {
  }

  /** Sends one probe request, retrying once if a pooled connection died before answering. */
  static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
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
