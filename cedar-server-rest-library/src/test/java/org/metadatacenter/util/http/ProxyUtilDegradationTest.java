package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.io.IOException;
import java.net.ServerSocket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProxyUtilDegradationTest {

  private final CedarRequestContext context = mock(CedarRequestContext.class);

  @Test
  void getClassifiesAConnectionFailureAsServiceUnavailable() throws IOException {
    assertServiceUnavailable(url -> ProxyUtil.proxyGet(url, context));
  }

  @Test
  void postClassifiesAConnectionFailureAsServiceUnavailable() throws IOException {
    assertServiceUnavailable(url -> ProxyUtil.proxyPost(url, context, "{}"));
  }

  @Test
  void putClassifiesAConnectionFailureAsServiceUnavailable() throws IOException {
    assertServiceUnavailable(url -> ProxyUtil.proxyPut(url, context, "{}"));
  }

  @Test
  void deleteClassifiesAConnectionFailureAsServiceUnavailable() throws IOException {
    assertServiceUnavailable(url -> ProxyUtil.proxyDelete(url, context));
  }

  private void assertServiceUnavailable(ProxyCall call) throws IOException {
    String url = unusedLocalUrl();

    CedarDependencyUnavailableException exception =
        assertThrows(CedarDependencyUnavailableException.class, () -> call.execute(url));

    assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, exception.getErrorPack().getStatus());
    assertEquals("Downstream service is unavailable", exception.getErrorPack().getMessage());
    assertTrue(exception.getErrorPack().getOriginalException() instanceof IOException);
  }

  /**
   * Reserve an ephemeral port and close it immediately. The subsequent request reaches the local
   * network stack but has no listener, giving the proxy a deterministic connection refusal without
   * depending on any CEDAR service or external network.
   */
  private String unusedLocalUrl() throws IOException {
    try (ServerSocket socket = new ServerSocket(0)) {
      return "http://127.0.0.1:" + socket.getLocalPort() + "/dependency";
    }
  }

  @FunctionalInterface
  private interface ProxyCall {
    void execute(String url) throws Exception;
  }
}
