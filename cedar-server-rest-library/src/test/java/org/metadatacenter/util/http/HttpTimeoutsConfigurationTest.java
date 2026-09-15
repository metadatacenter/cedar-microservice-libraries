package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.config.OutboundTimeoutOverride;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * That each class of outbound call is bounded by what the call is, and that a hop or a registry can
 * narrow its own class without opening a pool of its own.
 */
class HttpTimeoutsConfigurationTest {

  @Test
  void anExternalCallGetsLongerToConnectThanAHopToTheNextService() {
    assertEquals(1_000, HttpTimeouts.INTERACTIVE.connectTimeout().toMilliseconds());
    assertEquals(5_000, HttpTimeouts.EXTERNAL.connectTimeout().toMilliseconds(),
        "a second is a loopback's connect timeout, not a transatlantic TLS handshake's");
  }

  @Test
  void aBatchCallGetsTheTimeNobodyIsWaitingFor() {
    assertEquals(120_000, HttpTimeouts.BATCH.responseTimeout().toMilliseconds());
    assertNotEquals(HttpTimeouts.INTERACTIVE.responseTimeout().toMilliseconds(),
        HttpTimeouts.BATCH.responseTimeout().toMilliseconds());
  }

  @Test
  void anOverrideChangesOnlyWhatItStates() {
    HttpTimeouts narrowed = HttpTimeouts.EXTERNAL.with(new OutboundTimeoutOverride(null, 9_000));

    assertEquals(9_000, narrowed.responseTimeout().toMilliseconds());
    assertEquals(HttpTimeouts.EXTERNAL.connectTimeout().toMilliseconds(),
        narrowed.connectTimeout().toMilliseconds(),
        "a response-only override leaves the connect timeout where the class had it");
  }

  @Test
  void anEmptyOverrideIsTheClassItself() {
    assertSame(HttpTimeouts.EXTERNAL, HttpTimeouts.EXTERNAL.with(new OutboundTimeoutOverride()));
    assertSame(HttpTimeouts.EXTERNAL, HttpTimeouts.EXTERNAL.with(null));
  }
}
