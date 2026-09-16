package org.metadatacenter.server.service;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An authentication cache, so the tests that matter are the ones about forgetting rather than the
 * ones about remembering.
 */
class ApiKeyLookupCacheTest {

  private static FolderServerUser user(String id) {
    FolderServerUser user = new FolderServerUser();
    user.setId(id);
    return user;
  }

  @Test
  void aRememberedKeyAnswersWithoutTheGraph() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");
    FolderServerUser stored = user("https://example.org/users/1");

    cache.put("secret", stored);
    ApiKeyLookupCache.Lookup lookup = cache.get("secret");

    assertTrue(lookup.isPresent());
    assertSame(stored, lookup.getUser());
    assertEquals(1, cache.getHitCount());
  }

  @Test
  void anUnknownKeyIsAMiss() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");

    assertFalse(cache.get("never-seen").isPresent());
    assertEquals(1, cache.getMissCount());
  }

  /**
   * A key that authenticates nobody has to be remembered as such, or a client hammering a bad
   * credential reaches the graph on every attempt while a good one does not.
   */
  @Test
  void aKeyThatAuthenticatesNobodyIsRememberedAsNobody() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");

    cache.put("bad-key", null);
    ApiKeyLookupCache.Lookup lookup = cache.get("bad-key");

    assertTrue(lookup.isPresent(), "remembered");
    assertNull(lookup.getUser(), "as nobody");
  }

  @Test
  void anEntryStopsAnsweringWhenItsWindowPasses() throws Exception {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("0");
    // A zero TTL disables the cache outright, so the expiry path is exercised through the smallest
    // window the configuration accepts rather than by sleeping ten seconds.
    ApiKeyLookupCache shortLived = new ApiKeyLookupCache("1");
    shortLived.put("secret", user("https://example.org/users/1"));
    assertTrue(shortLived.get("secret").isPresent());

    Thread.sleep(1100);

    assertFalse(shortLived.get("secret").isPresent(), "the entry should have lapsed");
    assertEquals(0, shortLived.size(), "and been dropped rather than left behind");
    assertFalse(cache.isEnabled());
  }

  @Test
  void aTtlOfZeroTurnsTheCacheOff() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("0");

    cache.put("secret", user("https://example.org/users/1"));

    assertFalse(cache.isEnabled());
    assertFalse(cache.get("secret").isPresent(), "nothing is remembered when it is off");
    assertEquals(0, cache.size());
  }

  @Test
  void aRevokedKeyIsForgottenAtOnce() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");
    cache.put("secret", user("https://example.org/users/1"));

    cache.invalidateAll();

    assertFalse(cache.get("secret").isPresent());
    assertEquals(0, cache.size());
  }

  @Test
  void anUnreadableTtlFallsBackToTheDefaultRatherThanTurningTheCacheOff() {
    for (String value : new String[]{null, "", "   ", "not-a-number", "-5"}) {
      ApiKeyLookupCache cache = new ApiKeyLookupCache(value);
      assertTrue(cache.isEnabled(), "for value '" + value + "'");
    }
  }

  /** Negative caching lets an unknown caller choose the keys, so the map has to have a ceiling. */
  @Test
  void theCacheIsBounded() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("60");

    for (int i = 0; i < ApiKeyLookupCache.MAX_ENTRIES + 500; i++) {
      cache.put("key-" + i, null);
    }

    assertTrue(cache.size() <= ApiKeyLookupCache.MAX_ENTRIES,
        "size was " + cache.size());
  }

  @Test
  void aNullKeyIsNeverCachedOrLookedUp() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");

    cache.put(null, user("https://example.org/users/1"));

    assertEquals(0, cache.size());
    assertFalse(cache.get(null).isPresent());
  }

  /** Two different secrets must not collide, however the map is keyed. */
  @Test
  void keysAreDistinguishedFromEachOther() {
    ApiKeyLookupCache cache = new ApiKeyLookupCache("10");
    FolderServerUser first = user("https://example.org/users/1");
    FolderServerUser second = user("https://example.org/users/2");

    cache.put("secret-a", first);
    cache.put("secret-b", second);

    assertSame(first, cache.get("secret-a").getUser());
    assertSame(second, cache.get("secret-b").getUser());
  }
}
