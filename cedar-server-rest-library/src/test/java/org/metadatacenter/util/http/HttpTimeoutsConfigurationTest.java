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
  @Test
  void anInteractiveArtifactOverrideDoesNotShortenBatchCalls() {
    org.metadatacenter.config.CedarConfig config = org.mockito.Mockito.mock(
        org.metadatacenter.config.CedarConfig.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
    org.mockito.Mockito.when(config.getOutboundHttp()).thenReturn(new org.metadatacenter.config.OutboundHttpConfig());
    org.mockito.Mockito.when(config.getServers().getArtifact().getTimeouts())
        .thenReturn(new OutboundTimeoutOverride(null, 20000));
    org.mockito.Mockito.when(config.getExternalAuthorities().getTimeouts()).thenReturn(new OutboundTimeoutOverride());
    try {
      HttpTimeouts.install(config);
      assertEquals(20000, HttpTimeouts.ARTIFACT_INTERACTIVE.responseTimeout().toMilliseconds());
      assertEquals(120000, HttpTimeouts.ARTIFACT_BATCH.responseTimeout().toMilliseconds());
    } finally {
      HttpTimeouts.install(new org.metadatacenter.config.CedarConfig());
    }
  }

}
