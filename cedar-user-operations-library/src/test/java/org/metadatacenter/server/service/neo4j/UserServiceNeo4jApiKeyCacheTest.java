package org.metadatacenter.server.service.neo4j;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxyUser;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserApiKeyMap;
import org.metadatacenter.server.service.ApiKeyLookupCache;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What the cache is allowed to change about resolving an API key, and what it is not.
 */
class UserServiceNeo4jApiKeyCacheTest {

  private static final String KEY = "0000111122223333444455556666777788889999aaaabbbbccccddddeeeeffff";
  private static final String USER_ID = "https://example.org/users/1";

  private Neo4JProxyUser proxy;
  private UserServiceNeo4j service;

  private static FolderServerUser graphUser(String id, String apiKey, boolean enabled) {
    CedarUserApiKey key = new CedarUserApiKey();
    key.setKey(apiKey);
    key.setEnabled(enabled);
    CedarUserApiKeyMap map = new CedarUserApiKeyMap();
    map.put(apiKey, key);

    FolderServerUser user = new FolderServerUser();
    user.setId(id);
    user.setApiKeys(List.of(apiKey));
    user.setApiKeyMap(map);
    return user;
  }

  private static FolderServerUser graphUser(String apiKey, boolean enabled) {
    return graphUser(USER_ID, apiKey, enabled);
  }

  @BeforeEach
  void setUp() {
    proxy = mock(Neo4JProxyUser.class);
    service = new UserServiceNeo4j(proxy, ApiKeyLookupCache.withTtlSeconds(60));
  }

  /** The whole point: 5,666 identical lookups in ten minutes become one graph query. */
  @Test
  void repeatedLookupsOfTheSameKeyReachTheGraphOnce() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));

    for (int i = 0; i < 50; i++) {
      assertEquals(USER_ID, service.findUserByApiKey(KEY).getId());
    }

    verify(proxy, times(1)).findUserByApiKey(KEY);
  }

  /**
   * Each caller gets its own CedarUser. The resolver stamps the auth source on what it is handed,
   * so a shared instance would let one request's edit reach another's.
   */
  @Test
  void everyCallerGetsItsOwnUserObject() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));

    CedarUser first = service.findUserByApiKey(KEY);
    CedarUser second = service.findUserByApiKey(KEY);

    assertNotSame(first, second);
    assertEquals(first.getId(), second.getId());
  }

  /** A key the graph does not know is remembered as such, rather than asked again every time. */
  @Test
  void aKeyThatAuthenticatesNobodyIsNotAskedAgain() {
    when(proxy.findUserByApiKey(anyString())).thenReturn(null);

    assertNull(service.findUserByApiKey("bad-key"));
    assertNull(service.findUserByApiKey("bad-key"));

    verify(proxy, times(1)).findUserByApiKey("bad-key");
  }

  /**
   * The disabled-key rule is evaluated on every call rather than remembered as a verdict, so caching
   * cannot turn a withdrawn credential back on.
   */
  @Test
  void aDisabledKeyIsStillRefusedFromTheCache() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, false));

    assertNull(service.findUserByApiKey(KEY));
    assertNull(service.findUserByApiKey(KEY), "and again from the cached record");
  }

  @Test
  void deletingAKeyForgetsTheCachedAnswerImmediately() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));
    service.findUserByApiKey(KEY);

    service.deleteApiKey(null, "key-id");
    service.findUserByApiKey(KEY);

    verify(proxy, times(2)).findUserByApiKey(KEY);
  }

  @Test
  void everyWriteThroughThisServiceForgetsTheCachedAnswer() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));

    int expectedGraphCalls = 0;
    Runnable[] writes = {
        () -> service.addApiKey(null, null, 5),
        () -> service.regenerateApiKey(null, "key-id", "new", null),
        () -> service.deleteApiKey(null, "key-id"),
        () -> service.patchUser(null, null),
        () -> service.replaceRolesAndPermissions(null, List.of(), List.of()),
        () -> service.replaceUiPreferences(null, null),
        () -> service.setHomeFolderId(null, "folder"),
    };
    for (Runnable write : writes) {
      service.findUserByApiKey(KEY);
      expectedGraphCalls++;
      write.run();
      // The next lookup must go back to the graph, so the count rises once per write.
      verify(proxy, times(expectedGraphCalls)).findUserByApiKey(KEY);
    }
  }

  @Test
  void withTheCacheOffEveryLookupReachesTheGraph() {
    UserServiceNeo4j uncached = new UserServiceNeo4j(proxy, ApiKeyLookupCache.withTtlSeconds(0));
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));

    uncached.findUserByApiKey(KEY);
    uncached.findUserByApiKey(KEY);
    uncached.findUserByApiKey(KEY);

    verify(proxy, times(3)).findUserByApiKey(KEY);
  }

  @Test
  void aLookupThatFindsNothingIsNotWrittenToTheGraphSideAtAll() {
    when(proxy.findUserByApiKey(anyString())).thenReturn(null);

    service.findUserByApiKey("bad-key");

    verify(proxy, never()).createUser(org.mockito.ArgumentMatchers.any());
  }

  /** Two callers with different keys must not be handed each other's user. */
  @Test
  void differentKeysResolveToTheirOwnUsers() {
    String otherKey = "ffffeeeeddddccccbbbbaaaa99998888777766665555444433332222111100000";
    FolderServerUser other = graphUser("https://example.org/users/2", otherKey, true);

    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));
    when(proxy.findUserByApiKey(otherKey)).thenReturn(other);

    assertEquals(USER_ID, service.findUserByApiKey(KEY).getId());
    assertEquals("https://example.org/users/2", service.findUserByApiKey(otherKey).getId());
    assertEquals(USER_ID, service.findUserByApiKey(KEY).getId());
  }

  @Test
  void theCacheIsPerServiceInstance() {
    when(proxy.findUserByApiKey(KEY)).thenReturn(graphUser(KEY, true));
    UserServiceNeo4j other = new UserServiceNeo4j(proxy, ApiKeyLookupCache.withTtlSeconds(60));

    service.findUserByApiKey(KEY);
    other.findUserByApiKey(KEY);

    // Two JVMs, two caches: this is the reason a revocation is not estate-wide until the TTL passes.
    verify(proxy, times(2)).findUserByApiKey(KEY);
    assertEquals(USER_ID, service.findUserByApiKey(KEY).getId());
  }
}
