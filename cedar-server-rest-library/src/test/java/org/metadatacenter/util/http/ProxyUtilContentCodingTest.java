package org.metadatacenter.util.http;

import com.sun.net.httpserver.HttpServer;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProxyUtilContentCodingTest {

  @Test
  void interserviceGetRequestsIdentityEncoding() throws Exception {
    AtomicReference<String> acceptEncoding = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/artifact", exchange -> {
      acceptEncoding.set(exchange.getRequestHeaders().getFirst("Accept-Encoding"));
      byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, body.length);
      exchange.getResponseBody().write(body);
      exchange.close();
    });
    server.start();
    try (ClassicHttpResponse response = ProxyUtil.proxyGet(
        "http://127.0.0.1:" + server.getAddress().getPort() + "/artifact", Map.of())) {
      EntityUtils.consume(response.getEntity());
    } finally {
      server.stop(0);
    }

    assertEquals("identity", acceptEncoding.get());
  }
}
