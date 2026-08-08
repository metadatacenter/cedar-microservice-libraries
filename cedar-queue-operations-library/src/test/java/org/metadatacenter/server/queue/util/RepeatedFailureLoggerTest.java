package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The contract these tests pin down is a trade: a recurring queue failure must stay fully visible -
 * every occurrence logged, at ERROR, with a running total - while its stack trace is printed only
 * once. Suppressing the frames must never suppress the event, and the cause must stay identifiable
 * after the first occurrence, so an outage that changes character is still diagnosable.
 */
class RepeatedFailureLoggerTest {

  private static final String MESSAGE = "The consumer failed. Retrying in 10 seconds.";
  private static final String COUNT_NOUN = "failures";

  private RepeatedFailureLogger failureLogger;
  private RecordingLogger log;
  private Exception cause;

  @BeforeEach
  void setUp() {
    failureLogger = new RepeatedFailureLogger();
    log = new RecordingLogger();
    cause = new IllegalStateException("Failed to connect to 127.0.0.1:6379.");
  }

  @Test
  void firstOccurrenceCarriesTheStackTrace() {
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);

    RecordingLogger.Entry only = log.entry(0);
    assertTrue(only.hasStackTrace(), "the first occurrence must carry the cause");
    assertEquals(cause, only.throwable());
    assertTrue(only.message().contains("Subsequent occurrences are logged without a stack trace."),
        "the first occurrence should say that later ones are abbreviated: " + only.message());
  }

  @Test
  void laterOccurrencesOmitTheStackTrace() {
    for (int i = 0; i < 5; i++) {
      failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    }

    assertEquals(5, log.count(), "every occurrence must still be logged");
    assertEquals(1, log.withStackTrace().size(), "only one occurrence may carry a stack trace");
    for (RecordingLogger.Entry later : log.entries().subList(1, 5)) {
      assertFalse(later.hasStackTrace(), "later occurrences must not repeat the frames");
    }
  }

  @Test
  void laterOccurrencesKeepTheCauseTypeAndMessage() {
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);

    String later = log.entry(1).message();
    assertTrue(later.contains(IllegalStateException.class.getName()),
        "the cause type must survive: " + later);
    assertTrue(later.contains("Failed to connect to 127.0.0.1:6379."),
        "the cause message must survive: " + later);
  }

  /**
   * An outage can change character - a refused connection becoming a timeout, say. Dropping the
   * frames must not cost the ability to see that.
   */
  @Test
  void aDifferentCauseIsStillIdentifiableAfterTheFirstOccurrence() {
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    failureLogger.report(log, MESSAGE, COUNT_NOUN, new java.net.SocketTimeoutException("Read timed out"));

    String second = log.entry(1).message();
    assertTrue(second.contains("SocketTimeoutException"), "the new cause type must show: " + second);
    assertTrue(second.contains("Read timed out"), "the new cause message must show: " + second);
  }

  @Test
  void everyOccurrenceIsLoggedAtError() {
    for (int i = 0; i < 4; i++) {
      failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    }

    for (RecordingLogger.Entry entry : log.entries()) {
      assertEquals(Level.ERROR, entry.level(), "suppressing frames must not downgrade the event");
    }
  }

  @Test
  void everyOccurrenceCarriesTheRunningTotalAndTheCountNoun() {
    for (int i = 0; i < 3; i++) {
      failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    }

    List<RecordingLogger.Entry> entries = log.entries();
    for (int i = 0; i < entries.size(); i++) {
      String expected = "(" + (i + 1) + " failures since startup)";
      assertTrue(entries.get(i).message().contains(expected),
          "expected " + expected + " in: " + entries.get(i).message());
    }
  }

  @Test
  void theCountNounIsTheCallersWord() {
    failureLogger.report(log, "The log message could not be enqueued.", "dropped", cause);

    assertTrue(log.entry(0).message().contains("(1 dropped since startup)"), log.entry(0).message());
  }

  @Test
  void theCallerMessageIsPreserved() {
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);

    assertTrue(log.entry(0).message().startsWith(MESSAGE), log.entry(0).message());
  }

  @Test
  void reportReturnsTheRunningTotalAndGetCountAgrees() {
    assertEquals(1, failureLogger.report(log, MESSAGE, COUNT_NOUN, cause));
    assertEquals(2, failureLogger.report(log, MESSAGE, COUNT_NOUN, cause));
    assertEquals(3, failureLogger.report(log, MESSAGE, COUNT_NOUN, cause));
    assertEquals(3, failureLogger.getCount());
  }

  @Test
  void aFreshLoggerHasNotCountedAnything() {
    assertEquals(0, failureLogger.getCount());
    assertEquals(0, log.count());
  }

  /**
   * Each queue service and each consumer holds its own logger, so one service's outage must not
   * consume another's single stack trace.
   */
  @Test
  void separateLoggersCountAndTraceIndependently() {
    RepeatedFailureLogger other = new RepeatedFailureLogger();
    RecordingLogger otherLog = new RecordingLogger();

    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
    other.report(otherLog, MESSAGE, COUNT_NOUN, cause);

    assertEquals(2, failureLogger.getCount());
    assertEquals(1, other.getCount());
    assertEquals(1, otherLog.withStackTrace().size(), "the second logger keeps its own first trace");
  }

  /**
   * The real callers are request threads and consumer threads hitting one logger at once. Exactly
   * one of them may print the trace, and no occurrence may be lost from the total.
   */
  @Test
  void underConcurrencyExactlyOneOccurrenceCarriesTheStackTrace() throws Exception {
    int threads = 16;
    int perThread = 100;
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(threads);

    for (int t = 0; t < threads; t++) {
      new Thread(() -> {
        try {
          start.await();
          for (int i = 0; i < perThread; i++) {
            failureLogger.report(log, MESSAGE, COUNT_NOUN, cause);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          done.countDown();
        }
      }).start();
    }

    start.countDown();
    assertTrue(done.await(30, TimeUnit.SECONDS), "the reporting threads should finish promptly");

    int total = threads * perThread;
    assertEquals(total, failureLogger.getCount(), "no occurrence may be lost from the total");
    assertEquals(total, log.count(), "every occurrence must be logged");
    assertEquals(1, log.withStackTrace().size(), "exactly one thread may print the trace");
  }
}
