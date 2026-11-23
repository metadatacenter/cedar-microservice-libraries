package org.metadatacenter.server.search.elasticsearch.worker;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenWalkLedgerTest {

  private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-23T18:00:00Z"));

  private OpenWalkLedger ledger(int capacity) {
    return new OpenWalkLedger(capacity, Duration.ofSeconds(60), now::get);
  }

  private static OpenWalkLedger.ToClose none() {
    return new OpenWalkLedger.ToClose(List.of(), List.of());
  }

  private static OpenWalkLedger.ToClose abandoned(String... ids) {
    return new OpenWalkLedger.ToClose(List.of(), List.of(ids));
  }

  private void later(int seconds) {
    now.set(now.get().plusSeconds(seconds));
  }

  @Test
  void aWalkPastTheCapacityClosesTheOneThatWentLongestWithoutAPage() {
    OpenWalkLedger ledger = ledger(2);
    assertEquals(none(), ledger.use("a"));
    later(1);
    assertEquals(none(), ledger.use("b"));
    later(1);
    assertEquals(abandoned("a"), ledger.use("c"));
    assertEquals(2, ledger.size());
  }

  @Test
  void aPageKeepsItsWalkFromBeingTheOneClosed() {
    OpenWalkLedger ledger = ledger(2);
    ledger.use("a");
    later(1);
    ledger.use("b");
    later(1);
    ledger.use("a");
    later(1);
    assertEquals(abandoned("b"), ledger.use("c"));
  }

  @Test
  void aContextWhoseKeepAliveRanOutIsClosedWithoutCountingAgainstTheCapacity() {
    OpenWalkLedger ledger = ledger(2);
    ledger.use("a");
    later(30);
    ledger.use("b");
    later(31);
    // a has gone 61 s without a page. OpenSearch counts it until its reaper runs, so it is closed;
    // b is still open, and with a gone there is room for c.
    assertEquals(new OpenWalkLedger.ToClose(List.of("a"), List.of()), ledger.use("c"));
    assertEquals(2, ledger.size());
    later(1);
    assertEquals(abandoned("b"), ledger.use("d"));
  }

  @Test
  void aReleasedWalkNoLongerCounts() {
    OpenWalkLedger ledger = ledger(1);
    ledger.use("a");
    ledger.release("a");
    assertEquals(none(), ledger.use("b"));
  }

  @Test
  void aLedgerHoldsAtLeastOneWalk() {
    assertThrows(IllegalArgumentException.class, () -> ledger(0));
  }
}
