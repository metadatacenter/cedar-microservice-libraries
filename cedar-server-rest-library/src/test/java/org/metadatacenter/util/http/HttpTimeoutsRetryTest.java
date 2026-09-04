package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
