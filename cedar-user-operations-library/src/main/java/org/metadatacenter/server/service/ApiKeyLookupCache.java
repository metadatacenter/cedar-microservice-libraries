package org.metadatacenter.server.service;

import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A short-lived memory of which user an API key resolves to.
 * <p>
 * Every API-key-authenticated request resolves its caller's user record from the graph before doing
 * anything else, and the answer is the same every time. Profiled on production during a reindex on
 * 2026-09-15: <strong>the same key resolved 5,666 times in ten minutes</strong> - identical query,
 * identical parameters, identical answer - at 8.81 ms each, 50 seconds of Neo4j time in a ten-minute
 * window, and the most expensive repeated query measured anywhere in the system. Each of those is a
 * request re-authenticating from scratch.
 * <p>
 * <strong>The trade this makes.</strong> Caching an authentication answer means a key that is
 * disabled, deleted or regenerated keeps working until its entry expires. That is why the window is
 * seconds rather than minutes: at the measured rate a 10-second entry removes about 99% of the
 * lookups, and a longer one buys almost nothing while widening the window in which a withdrawn
 * credential still works. {@code CEDAR_API_KEY_CACHE_TTL_SECONDS} sets it, and <strong>0 turns the
 * cache off</strong> - the lookup then behaves exactly as it did before this class existed.
 * <p>
 * <strong>What it cannot do.</strong> The cache is per-JVM and every service has one. A key revoked
 * through the user server clears that server's cache at once ({@code UserServiceNeo4j} invalidates on
 * every mutation it performs) but not the other fourteen, which wait out the TTL. Anything needing a
 * revocation to be immediate estate-wide has to restart the services, and the TTL is short precisely
 * so that is never the answer.
 * <p>
 * Negative answers are cached too, on the same window: a key that authenticates nobody otherwise
 * reaches the graph on every attempt, which makes a client hammering a bad credential more expensive
 * than a client using a good one. This cannot delay a newly created key, because a key nobody has
 * tried has no entry to be stale.
 * <p>
 * Keys are stored as a SHA-256 hash rather than the secret itself. The secret is in the process's
 * memory regardless, but nothing is served by writing it into a long-lived map, and a hash costs
 * microseconds against the 8.81 ms it avoids. <strong>An API key must never be logged</strong>, so
 * neither the key nor its hash appears in any message here.
 */
public class ApiKeyLookupCache {

  private static final Logger log = LoggerFactory.getLogger(ApiKeyLookupCache.class);

  static final String TTL_ENV_VARIABLE = "CEDAR_API_KEY_CACHE_TTL_SECONDS";

  /**
   * Ten seconds. At the measured 9.4 lookups per second for one key this turns 5,666 lookups into
   * about 60 - some 99% of the cost - and thirty seconds would take that to 99.6%. The remaining
   * 0.6% is not worth tripling the time a withdrawn key keeps working.
   */
  static final long DEFAULT_TTL_SECONDS = 10;

  /**
   * A bound, because negative caching means an unknown caller can choose the keys. At 10 seconds a
   * client would have to offer a thousand distinct bad keys a second to reach this, and reaching it
   * costs nothing worse than not caching.
   */
  static final int MAX_ENTRIES = 10_000;

  private record Entry(FolderServerUser user, long expiresAtNanos) {
    boolean isLive(long now) {
      return now < expiresAtNanos;
    }
  }

  private final Map<String, Entry> entries = new ConcurrentHashMap<>();
  private final long ttlNanos;
  private final AtomicLong hits = new AtomicLong();
  private final AtomicLong misses = new AtomicLong();

  public ApiKeyLookupCache() {
    this(System.getenv(TTL_ENV_VARIABLE));
  }

  /**
   * A cache with an explicit window, for a caller that configures it itself rather than from the
   * environment - tests, and anything that has already resolved the value.
   */
  public static ApiKeyLookupCache withTtlSeconds(long seconds) {
    return new ApiKeyLookupCache(Long.toString(seconds));
  }

  ApiKeyLookupCache(String configuredTtlSeconds) {
    this.ttlNanos = parseTtlSeconds(configuredTtlSeconds) * 1_000_000_000L;
    if (ttlNanos == 0) {
      log.info("API key lookup caching is disabled ({}=0)", TTL_ENV_VARIABLE);
    } else {
      log.info("API key lookups are cached for {}s ({})", ttlNanos / 1_000_000_000L, TTL_ENV_VARIABLE);
    }
  }

  private static long parseTtlSeconds(String configured) {
    if (configured == null || configured.isBlank()) {
      return DEFAULT_TTL_SECONDS;
    }
    try {
      long seconds = Long.parseLong(configured.trim());
      if (seconds < 0) {
        // An authentication cache is not the place to guess at intent. Refusing the value and
        // saying so is better than silently choosing one.
        log.warn("{} is negative ({}); using the default of {}s", TTL_ENV_VARIABLE, seconds, DEFAULT_TTL_SECONDS);
        return DEFAULT_TTL_SECONDS;
      }
      return seconds;
    } catch (NumberFormatException e) {
      log.warn("{} is not a number ({}); using the default of {}s", TTL_ENV_VARIABLE, configured, DEFAULT_TTL_SECONDS);
      return DEFAULT_TTL_SECONDS;
    }
  }

  public boolean isEnabled() {
    return ttlNanos > 0;
  }

  /**
   * @return the cached graph record for this key, {@link Lookup#miss()} when nothing is remembered,
   * and a hit carrying a null user when the key is remembered as authenticating nobody.
   */
  public Lookup get(String apiKey) {
    if (!isEnabled() || apiKey == null) {
      return Lookup.miss();
    }
    String hash = hash(apiKey);
    Entry entry = entries.get(hash);
    if (entry == null) {
      misses.incrementAndGet();
      return Lookup.miss();
    }
    if (!entry.isLive(System.nanoTime())) {
      // Remove the exact entry seen, so a concurrent put of a fresh one is not thrown away.
      entries.remove(hash, entry);
      misses.incrementAndGet();
      return Lookup.miss();
    }
    hits.incrementAndGet();
    return Lookup.of(entry.user());
  }

  /** Remembers what this key resolves to, {@code user} being null for a key that authenticates nobody. */
  public void put(String apiKey, FolderServerUser user) {
    if (!isEnabled() || apiKey == null) {
      return;
    }
    if (entries.size() >= MAX_ENTRIES) {
      purgeExpired();
      if (entries.size() >= MAX_ENTRIES) {
        return;
      }
    }
    entries.put(hash(apiKey), new Entry(user, System.nanoTime() + ttlNanos));
  }

  /**
   * Forgets everything, called by every mutation the owning service performs.
   * <p>
   * Everything rather than one user's keys, because the cache is keyed by key hash and a mutation
   * names a user. Building the reverse index to be precise would cost more than it saves: mutations
   * are administrative and rare, entries live ten seconds, and a cleared cache costs one graph
   * lookup per key in flight.
   */
  public void invalidateAll() {
    if (!entries.isEmpty()) {
      entries.clear();
    }
  }

  private void purgeExpired() {
    long now = System.nanoTime();
    entries.values().removeIf(entry -> !entry.isLive(now));
  }

  private static String hash(String apiKey) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is required of every JVM; this cannot happen on a JVM that can run CEDAR.
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  public long getHitCount() {
    return hits.get();
  }

  public long getMissCount() {
    return misses.get();
  }

  int size() {
    return entries.size();
  }

  /** Distinguishes "nothing remembered" from "remembered as nobody", which a null user cannot. */
  public static final class Lookup {

    private static final Lookup MISS = new Lookup(false, null);

    private final boolean present;
    private final FolderServerUser user;

    private Lookup(boolean present, FolderServerUser user) {
      this.present = present;
      this.user = user;
    }

    static Lookup miss() {
      return MISS;
    }

    static Lookup of(FolderServerUser user) {
      return new Lookup(true, user);
    }

    public boolean isPresent() {
      return present;
    }

    public FolderServerUser getUser() {
      return user;
    }
  }
}
