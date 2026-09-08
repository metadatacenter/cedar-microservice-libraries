package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.client5.http.fluent.Request;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Proves that one outbound call cannot leave cookie state for the next caller. */
class HttpTimeoutsCookieTest {

  @Test
  void aCookieSetByAnUpstreamIsNotReplayed() throws Exception {
    AtomicInteger requestCount = new AtomicInteger();
    AtomicReference<String> secondRequestCookie = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/dependency", exchange -> {
      if (requestCount.incrementAndGet() == 1) {
        exchange.getResponseHeaders().add("Set-Cookie", "upstream-session=secret; Path=/; HttpOnly");
      } else {
        secondRequestCookie.set(exchange.getRequestHeaders().getFirst("Cookie"));
      }
      exchange.sendResponseHeaders(204, -1);
      exchange.close();
    });
    server.start();

    try {
      HttpTimeouts timeouts = new HttpTimeouts(1_000, 1_000, 5_000, 1, 1);
      String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/dependency";

      assertEquals(204, timeouts.execute(Request.get(url)).getCode());
      assertEquals(204, timeouts.execute(Request.get(url)).getCode());

      assertEquals(2, requestCount.get());
      assertNull(secondRequestCookie.get(), "the process-wide client must not retain upstream cookies");
    } finally {
      server.stop(0);
    }
  }
}
