package org.metadatacenter.server.search.util;

import org.metadatacenter.model.CedarResourceType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * How far an index rebuild has got: written by the job as it runs, read by whoever polls its status.
 *
 * <p>The rebuild already computed this and threw it away. It logs {@code Progress: N%} every hundred
 * resources, to the resource server's log and nowhere else, so the only way to tell an eight-hour job
 * apart from a wedged one was to have shell access to the machine running it. Everything here is
 * already known at the point it is recorded; the change is that it is kept.
 *
 * <p><b>Why the rate is a trailing window.</b> An all-time average is wrong for most of a run: the
 * first phases write no documents at all, and a batch flush every thousand resources arrives as a
 * periodic stall. Averaging over the last {@link #WINDOW} samples — two batch flushes' worth of
 * resources — tracks the rate the job is actually achieving now, which is what an estimate has to be
 * built from.
 *
 * <p><b>Why the estimate does not model cost per resource type.</b> An instance costs far more than a
 * folder: two artifact-server fetches and a parse against almost nothing. That would matter if the
 * expensive resources were grouped, because the rate would then differ systematically between the
 * start of the run and the end. They are not: the work list is ordered by lowercased name, which is
 * uncorrelated with type, so every type is interleaved evenly throughout and the trailing window
 * already sees a representative mix. The per-type counts are reported because they say what the
 * repository holds and let a reader sanity-check the total — not because the estimate needs them.
 *
 * <p><b>Threads.</b> One writer — the job's own thread — and any number of readers on HTTP threads.
 * The counters are written on the hot path, once per resource, so they are plain fields and atomics
 * rather than anything synchronized; only the sample window takes a lock, and only once every
 * {@link #SAMPLE_EVERY} resources. {@link #snapshot()} reads fields that are updated independently,
 * so a snapshot can catch the counter a resource or two ahead of the timestamp beside it. That is
 * accepted: this is a progress report, and paying for a consistent cross-field read on every one of
 * half a million increments would cost more than the inconsistency does.
 */
public final class IndexingProgress {

  /** Reported instead of zero when a total is not yet known, which would otherwise read as "nothing to do". */
  public static final long UNKNOWN = -1;

  /** How many units pass between rate samples. Matches the cadence of the existing progress log line. */
  private static final int SAMPLE_EVERY = 100;

  /** How many samples the rate is averaged over. 20 × 100 = 2000 resources, or two batch flushes. */
  private static final int WINDOW = 20;

  private final Supplier<Instant> clock;
  private final Instant startedAt;

  private volatile IndexingPhase phase = IndexingPhase.PENDING;
  private volatile Instant lastUpdatedAt;

  /**
   * The current phase's own counter and denominator. Reset when the phase changes, because what is
   * being counted changes with it.
   */
  private volatile long processed;
  private volatile long total = UNKNOWN;

  /**
   * The work list broken down by type, fixed once enumeration has produced it, and the running count
   * against it. Both span the whole indexing phase rather than resetting with the per-phase counter.
   * Every constant is present from construction so that no reader ever sees the map being built.
   */
  private volatile Map<CedarResourceType, Long> totalByType = Map.of();
  private final Map<CedarResourceType, AtomicLong> processedByType = new EnumMap<>(CedarResourceType.class);

  /** {@code (processed, epochMilli)} samples, oldest first. Guarded by itself. */
  private final Deque<long[]> samples = new ArrayDeque<>();

  public IndexingProgress() {
    this(Instant::now);
  }

  /**
   * A record that begins at a given instant and runs on the wall clock thereafter.
   *
   * <p>The guard claims an index at an instant it is given, and the record's first heartbeat has to be
   * that same instant: the two are compared to decide whether a job has stalled, and a record that
   * started itself a moment later would answer that question against a different clock.
   */
  public IndexingProgress(Instant startedAt) {
    this.clock = Instant::now;
    this.startedAt = startedAt;
    this.lastUpdatedAt = startedAt;
    for (CedarResourceType type : CedarResourceType.values()) {
      processedByType.put(type, new AtomicLong());
    }
  }

  /** The clock is supplied so a test can advance time without sleeping through a window. */
  public IndexingProgress(Supplier<Instant> clock) {
    this.clock = clock;
    this.startedAt = clock.get();
    this.lastUpdatedAt = this.startedAt;
    for (CedarResourceType type : CedarResourceType.values()) {
      processedByType.put(type, new AtomicLong());
    }
  }

  /**
   * Move to the next step, resetting the counter and the rate window with it. Also a heartbeat: a
   * phase that counts nothing — promoting an index, say — still reports that the job is alive.
   */
  public void enterPhase(IndexingPhase next) {
    synchronized (samples) {
      samples.clear();
    }
    processed = 0;
    total = UNKNOWN;
    phase = next;
    lastUpdatedAt = clock.get();
  }

  /** Name the denominator for the current phase, once it is known. */
  public void setTotal(long value) {
    total = value;
    lastUpdatedAt = clock.get();
  }

  /**
   * Record the work list's shape, once enumeration has produced it. Only types the list actually
   * contains are kept, so a status page is not made to render a row of zeroes for users, groups and
   * messages, none of which are filesystem resources.
   */
  public void setTotalByType(Map<CedarResourceType, Long> counts) {
    Map<CedarResourceType, Long> present = new EnumMap<>(CedarResourceType.class);
    counts.forEach((type, count) -> {
      if (count != null && count > 0) {
        present.put(type, count);
      }
    });
    totalByType = Map.copyOf(present);
    lastUpdatedAt = clock.get();
  }

  /** One more unit of the current phase is done. Called once per resource on the indexing path. */
  public void advance() {
    advance(null);
  }

  /** One more, of a known type, so the per-type breakdown advances with the total. */
  public void advance(CedarResourceType type) {
    long done = ++processed;
    if (type != null) {
      processedByType.get(type).incrementAndGet();
    }
    if (done % SAMPLE_EVERY == 0) {
      Instant now = clock.get();
      lastUpdatedAt = now;
      synchronized (samples) {
        samples.addLast(new long[]{done, now.toEpochMilli()});
        while (samples.size() > WINDOW) {
          samples.removeFirst();
        }
      }
    }
  }

  public IndexingPhase getPhase() {
    return phase;
  }

  /**
   * When the job last said anything. This is what tells a slow rebuild from a stopped one, and it is
   * the reading a deadline should be taken against rather than elapsed time: an eight-hour rebuild is
   * healthy, and an eight-minute one that has not moved since minute two is not.
   */
  public Instant getLastUpdatedAt() {
    return lastUpdatedAt;
  }

  /**
   * The rate the job is achieving now, or empty until the window holds enough to say. Null rather
   * than zero: "not measured yet" and "making no progress" are different answers, and only the
   * second is a reason to worry.
   */
  private Double ratePerSecond() {
    long[] oldest;
    long[] newest;
    synchronized (samples) {
      if (samples.size() < 2) {
        return null;
      }
      oldest = samples.peekFirst();
      newest = samples.peekLast();
    }
    long units = newest[0] - oldest[0];
    long millis = newest[1] - oldest[1];
    if (units <= 0 || millis <= 0) {
      return null;
    }
    return (units * 1000.0) / millis;
  }

  /** What the status routes render. Timestamps are strings so this reads the same over HTTP as in Java. */
  public record Snapshot(String phase, String phaseDescription, long processed, long total,
                         Double percentComplete, Map<String, Long> totalByType,
                         Map<String, Long> processedByType, String startedAt, String lastUpdatedAt,
                         Double unitsPerSecond, Long secondsRemaining, String estimatedFinishAt) {
  }

  public Snapshot snapshot() {
    IndexingPhase currentPhase = phase;
    long done = processed;
    long denominator = total;
    Double rate = ratePerSecond();

    Double percent = null;
    Long secondsRemaining = null;
    String finishAt = null;
    if (denominator > 0) {
      percent = Math.min(100.0, (100.0 * done) / denominator);
      long remaining = Math.max(0, denominator - done);
      if (rate != null && rate > 0) {
        secondsRemaining = Math.round(remaining / rate);
        finishAt = clock.get().plus(Duration.ofSeconds(secondsRemaining)).toString();
      }
    }

    Map<String, Long> totals = new LinkedHashMap<>();
    Map<String, Long> counted = new LinkedHashMap<>();
    totalByType.forEach((type, count) -> {
      totals.put(type.getValue(), count);
      counted.put(type.getValue(), processedByType.get(type).get());
    });

    return new Snapshot(currentPhase.name(), currentPhase.getDescription(), done, denominator, percent,
        Map.copyOf(totals), Map.copyOf(counted), startedAt.toString(), lastUpdatedAt.toString(),
        rate, secondsRemaining, finishAt);
  }
}
