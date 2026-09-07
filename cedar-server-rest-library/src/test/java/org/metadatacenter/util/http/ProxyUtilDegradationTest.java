package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.io.IOException;

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
    String url = unavailableLocalUrl();

    CedarDependencyUnavailableException exception =
        assertThrows(CedarDependencyUnavailableException.class, () -> call.execute(url));

    assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, exception.getErrorPack().getStatus());
    assertEquals("Downstream service is unavailable", exception.getErrorPack().getMessage());
    assertTrue(exception.getErrorPack().getOriginalException() instanceof IOException);
  }

  /**
   * Port 1 is the backend-test convention for a deliberately unavailable loopback dependency.
   */
  private String unavailableLocalUrl() {
    return "http://127.0.0.1:1/dependency";
  }

  @FunctionalInterface
  private interface ProxyCall {
    void execute(String url) throws Exception;
  }
}
