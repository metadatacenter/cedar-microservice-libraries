package org.metadatacenter.server.cache.user;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.CedarUserSummary;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * What the cache does when a lookup fails, and for how long it remembers.
 *
 * <p>Resolving a display name that cannot be resolved used to cost a 20-second socket timeout every
 * time it was asked for, and it is asked for a great deal: three ids per resource, repeated for every
 * ancestor on a path and every entry of a listing. Guava caches a value and never a failure, so
 * nothing stopped the repetition. The resource-server suite stopped finishing and had to be killed by
 * hand. Remembering a failed id for a minute is the whole fix, and until now the only thing standing
 * guard over it was that the suite completes — which reports a regression as a CI timeout rather than
 * as a failing assertion, hours after the fact and pointing nowhere in particular.
 *
 * <p>These assert it directly instead: a failure is not retried inside the window, a summary supplied
 * directly clears the record, and the record expires on time. Retention is asserted by advancing a
 * clock rather than by sleeping, so the boundary is stated rather than approached.
 */
public class UserSummaryCacheTest {

  private static final String ID = "https://metadatacenter.org/users/11111111-2222-3333-4444-555555555555";
  private static final long RETENTION_SECONDS = 60;

  /** A clock the test moves by hand, so an expiry can be asserted at its boundary. */
  private static final class MovableTicker extends Ticker {
    private long nanos = 0;

    @Override
    public long read() {
      return nanos;
    }

    void advance(long amount, TimeUnit unit) {
      nanos += unit.toNanos(amount);
    }
  }

  /** Records what it was asked for, so "was the loader called again" is a question the test can ask. */
  private static final class RecordingLoader implements UserSummaryCache.SummaryLoader {
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private CedarUserSummary answer;

    RecordingLoader(CedarUserSummary answer) {
      this.answer = answer;
    }

    @Override
    public CedarUserSummary load(String id) {
      asked.add(id);
      return answer;
    }

    int callsFor(String id) {
      return (int) asked.stream().filter(id::equals).count();
    }
  }

  private MovableTicker clock;

  @BeforeEach
  public void freshClock() {
    clock = new MovableTicker();
  }

  private static CedarUserSummary summary(String id, String screenName) {
    CedarUserSummary summary = new CedarUserSummary();
    summary.setId(id);
    summary.setScreenName(screenName);
    return summary;
  }

  /**
   * The property the whole mechanism exists for: a lookup that failed is not tried again inside the
   * retention window. Without it each repeat paid the socket timeout, which is what stopped the suite
   * from finishing.
   */
  @Test
  public void aFailedLookupIsNotRetriedInsideTheRetentionWindow() {
    RecordingLoader loader = new RecordingLoader(null);
    UserSummaryCache.buildForTesting(loader, clock, RETENTION_SECONDS);

    assertNull(UserSummaryCache.getInstance().getUser(ID), "an unresolvable id has no summary");
    assertEquals(1, loader.callsFor(ID), "the first lookup must reach the loader");

    for (int i = 0; i < 5; i++) {
      assertNull(UserSummaryCache.getInstance().getUser(ID));
    }
    assertEquals(1, loader.callsFor(ID),
        "an id already known to be unresolvable must not reach the loader again inside the window");
  }

  /** The record is not permanent: once it expires the id is worth asking about again. */
  @Test
  public void theRecordExpiresAndTheIdIsTriedAgain() {
    RecordingLoader loader = new RecordingLoader(null);
    UserSummaryCache.buildForTesting(loader, clock, RETENTION_SECONDS);

    assertNull(UserSummaryCache.getInstance().getUser(ID));
    assertEquals(1, loader.callsFor(ID));

    clock.advance(RETENTION_SECONDS - 1, TimeUnit.SECONDS);
    assertNull(UserSummaryCache.getInstance().getUser(ID));
    assertEquals(1, loader.callsFor(ID), "still inside the window, so still not retried");

    clock.advance(2, TimeUnit.SECONDS);
    assertNull(UserSummaryCache.getInstance().getUser(ID));
    assertEquals(2, loader.callsFor(ID), "past the window, the id is tried again");
  }

  /**
   * A user server that comes back, or an account that is created, must show names again without
   * waiting out the window — which is what {@code put} is for. Left standing, the record would shadow
   * the summary, since a lookup consults it first.
   */
  @Test
  public void putClearsTheRecordBeforeItExpires() {
    RecordingLoader loader = new RecordingLoader(null);
    UserSummaryCache.buildForTesting(loader, clock, RETENTION_SECONDS);

    assertNull(UserSummaryCache.getInstance().getUser(ID));
    assertEquals(1, loader.callsFor(ID));

    UserSummaryCache.getInstance().put(summary(ID, "Test User"));

    CedarUserSummary found = UserSummaryCache.getInstance().getUser(ID);
    assertNotNull(found, "the summary just supplied must be served, not shadowed by the record");
    assertEquals("Test User", found.getScreenName());
    assertEquals(1, loader.callsFor(ID), "and it must be served from the cache, without the loader");
  }

  /** A resolvable id is cached as a value, so it reaches the loader once however often it is asked for. */
  @Test
  public void aResolvedSummaryIsServedFromTheCache() {
    RecordingLoader loader = new RecordingLoader(summary(ID, "Test User"));
    UserSummaryCache.buildForTesting(loader, clock, RETENTION_SECONDS);

    for (int i = 0; i < 5; i++) {
      assertEquals("Test User", UserSummaryCache.getInstance().getUser(ID).getScreenName());
    }
    assertEquals(1, loader.callsFor(ID), "a resolved id is fetched once and then served from the cache");
  }

  /** A null id is not a lookup. It must not reach the loader or be recorded as unresolvable. */
  @Test
  public void aNullIdIsNotALookup() {
    RecordingLoader loader = new RecordingLoader(null);
    UserSummaryCache.buildForTesting(loader, clock, RETENTION_SECONDS);

    assertNull(UserSummaryCache.getInstance().getUser(null));
    assertEquals(0, loader.asked.size(), "a null id must not reach the loader");
  }
}
