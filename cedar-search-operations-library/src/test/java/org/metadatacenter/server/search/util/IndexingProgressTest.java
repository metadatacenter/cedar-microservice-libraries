package org.metadatacenter.server.search.util;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.CedarResourceType;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexingProgressTest {

  /** A clock a test moves by hand, so a rate can be measured without waiting for one. */
  private static final class TestClock {
    private Instant now = Instant.parse("2026-09-14T00:00:00Z");

    Instant get() {
      return now;
    }

    void advance(Duration by) {
      now = now.plus(by);
    }
  }

  private final TestClock clock = new TestClock();
  private final IndexingProgress progress = new IndexingProgress(clock::get);

  @Test
  void startsPendingWithNothingKnown() {
    IndexingProgress.Snapshot snapshot = progress.snapshot();

    assertEquals(IndexingPhase.PENDING.name(), snapshot.phase());
    assertEquals(0, snapshot.processed());
    assertEquals(IndexingProgress.UNKNOWN, snapshot.total());
    assertNull(snapshot.percentComplete(), "a percentage of an unknown total would be invented");
    assertNull(snapshot.unitsPerSecond());
    assertNull(snapshot.secondsRemaining());
  }

  @Test
  void reportsThePhaseAndItsDescription() {
    progress.enterPhase(IndexingPhase.ENUMERATING);

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertEquals(IndexingPhase.ENUMERATING.name(), snapshot.phase());
    assertEquals(IndexingPhase.ENUMERATING.getDescription(), snapshot.phaseDescription());
  }

  @Test
  void countsAgainstTheTotalOfTheCurrentPhase() {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(200);
    for (int i = 0; i < 50; i++) {
      progress.advance();
    }

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertEquals(50, snapshot.processed());
    assertEquals(200, snapshot.total());
    assertEquals(25.0, snapshot.percentComplete(), 0.001);
  }

  @Test
  void aNewPhaseStartsItsOwnCount() {
    progress.enterPhase(IndexingPhase.ENUMERATING);
    progress.setTotal(500);
    for (int i = 0; i < 500; i++) {
      progress.advance();
    }

    progress.enterPhase(IndexingPhase.INDEXING);

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertEquals(0, snapshot.processed(), "the indexing phase counts resources, not the pages before it");
    assertEquals(IndexingProgress.UNKNOWN, snapshot.total());
    assertNull(snapshot.percentComplete());
  }

  @Test
  void percentageIsCappedWhenMoreArriveThanWereExpected() {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(10);
    for (int i = 0; i < 25; i++) {
      progress.advance();
    }

    assertEquals(100.0, progress.snapshot().percentComplete(), 0.001);
    assertEquals(0L, progress.snapshot().secondsRemaining() == null ? 0L : progress.snapshot().secondsRemaining());
  }

  @Test
  void reportsOnlyTheTypesTheWorkListHolds() {
    Map<CedarResourceType, Long> counts = new EnumMap<>(CedarResourceType.class);
    counts.put(CedarResourceType.INSTANCE, 900L);
    counts.put(CedarResourceType.FOLDER, 100L);
    counts.put(CedarResourceType.USER, 0L);

    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotalByType(counts);
    progress.advance(CedarResourceType.INSTANCE);
    progress.advance(CedarResourceType.INSTANCE);
    progress.advance(CedarResourceType.FOLDER);

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertEquals(Map.of("instance", 900L, "folder", 100L), snapshot.totalByType());
    assertEquals(Map.of("instance", 2L, "folder", 1L), snapshot.processedByType());
    assertFalse(snapshot.totalByType().containsKey("user"), "a type the work list has none of is not a row");
  }

  @Test
  void measuresTheRateAndEstimatesFromIt() {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(1000);

    // Two samples are taken, at 100 and at 200, a second apart: 100 resources per second.
    for (int i = 0; i < 100; i++) {
      progress.advance();
    }
    clock.advance(Duration.ofSeconds(1));
    for (int i = 0; i < 100; i++) {
      progress.advance();
    }

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertEquals(100.0, snapshot.unitsPerSecond(), 0.001);
    assertEquals(8L, snapshot.secondsRemaining(), "800 left at 100 a second");
    assertNotNull(snapshot.estimatedFinishAt());
  }

  @Test
  void hasNoRateUntilTwoSamplesExist() {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(1000);
    for (int i = 0; i < 100; i++) {
      progress.advance();
    }
    clock.advance(Duration.ofSeconds(1));

    IndexingProgress.Snapshot snapshot = progress.snapshot();
    assertNull(snapshot.unitsPerSecond(), "one sample measures no interval");
    assertNull(snapshot.secondsRemaining());
  }

  @Test
  void theRateFollowsTheRecentPastRatherThanTheWholeRun() {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(100_000);

    // A slow start: 2000 resources at 10 a second.
    for (int batch = 0; batch < 20; batch++) {
      for (int i = 0; i < 100; i++) {
        progress.advance();
      }
      clock.advance(Duration.ofSeconds(10));
    }
    assertEquals(10.0, progress.snapshot().unitsPerSecond(), 0.001);

    // Then it speeds up. Once the window has refilled, the early slowness is gone from the estimate.
    for (int batch = 0; batch < 20; batch++) {
      for (int i = 0; i < 100; i++) {
        progress.advance();
      }
      clock.advance(Duration.ofSeconds(1));
    }

    assertEquals(100.0, progress.snapshot().unitsPerSecond(), 0.001,
        "an all-time average would still be reporting the slow start");
  }

  @Test
  void aPhaseChangeIsItselfAHeartbeat() {
    Instant before = progress.getLastUpdatedAt();
    clock.advance(Duration.ofMinutes(5));
    progress.enterPhase(IndexingPhase.PROMOTING);

    assertTrue(progress.getLastUpdatedAt().isAfter(before),
        "a phase that counts nothing must still show the job is alive");
  }

  @Test
  void aSnapshotTakenWhileTheJobRunsIsReadable() throws Exception {
    progress.enterPhase(IndexingPhase.INDEXING);
    progress.setTotal(10_000);
    CountDownLatch started = new CountDownLatch(1);

    Thread job = new Thread(() -> {
      started.countDown();
      for (int i = 0; i < 10_000; i++) {
        progress.advance(CedarResourceType.INSTANCE);
      }
    });
    job.start();
    assertTrue(started.await(5, TimeUnit.SECONDS));

    // Reading from another thread while the counter moves must not throw or see a broken map.
    for (int i = 0; i < 200; i++) {
      IndexingProgress.Snapshot snapshot = progress.snapshot();
      assertTrue(snapshot.processed() >= 0 && snapshot.processed() <= 10_000);
    }
    job.join(10_000);

    assertEquals(10_000, progress.snapshot().processed());
  }
}
