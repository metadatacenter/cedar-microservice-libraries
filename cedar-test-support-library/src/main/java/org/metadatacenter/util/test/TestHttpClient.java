package org.metadatacenter.util.test;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * The HTTP client the server tests share.
 *
 * <p>Leave connection recovery to the JDK and propagate failures from its send unchanged. An EOF
 * can follow response headers or part of a body; even an empty response does not prove that the
 * server did not apply the request. Replaying it here can duplicate a write and conceal the failure
 * the test should observe.
 */
public final class TestHttpClient {

  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  private TestHttpClient() {
  }

  /** Sends through the JDK client without an additional application-level retry. */
  public static HttpResponse<String> send(HttpRequest request) throws IOException, InterruptedException {
    return CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
  }

}
