package org.metadatacenter.server.service.neo4j;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.neo4j.proxy.Neo4JProxyUser;
import org.metadatacenter.server.security.model.user.*;
import org.metadatacenter.server.service.ApiKeyLookupCache;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiKeyInvalidationMatrixTest {
  private static final String KEY = "test-credential";

  private static FolderServerUser user() {
    CedarUserApiKey key = new CedarUserApiKey();
    key.setKey(KEY);
    key.setEnabled(true);
    CedarUserApiKeyMap keys = new CedarUserApiKeyMap();
    keys.put(KEY, key);
    FolderServerUser user = new FolderServerUser();
    user.setId("https://example.org/users/1");
    user.setApiKeys(List.of(KEY));
    user.setApiKeyMap(keys);
    return user;
  }

  enum Mutation {
    CREATE, ADD, REGENERATE, DELETE, PROFILE, ROLES, PREFERENCES, HOME;
    void apply(UserServiceNeo4j target) {
      switch (this) {
        case CREATE -> target.createUser(null);
        case ADD -> target.addApiKey(null, null, 5);
        case REGENERATE -> target.regenerateApiKey(null, "key-id", "new", null);
        case DELETE -> target.deleteApiKey(null, "key-id");
        case PROFILE -> target.patchUser(null, null);
        case ROLES -> target.replaceRolesAndPermissions(null, List.of(), List.of());
        case PREFERENCES -> target.replaceUiPreferences(null, null);
        case HOME -> target.setHomeFolderId(null, "folder");
      }
    }
  }
  enum Change { REVOKED, GRANTED, PERMISSIONS }

  static Stream<Arguments> overlappingChanges() {
    return Stream.of(Mutation.values()).flatMap(m -> Stream.of(Change.values()).map(c -> Arguments.of(m, c)));
  }

  @ParameterizedTest
  @MethodSource("overlappingChanges")
  void oldLookupCannotRepopulateInvalidatedCache(Mutation mutation, Change change) throws Exception {
    FolderServerUser before = change == Change.GRANTED ? null : user();
    FolderServerUser after = change == Change.REVOKED ? null : user();
    if (after != null) after.setPermissions(List.of("current-permission"));
    AtomicReference<FolderServerUser> graph = new AtomicReference<>(before);
    AtomicBoolean first = new AtomicBoolean(true);
    CountDownLatch read = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    // Every mutation invalidates the entire graph-record cache. Exercise each kind of cached
    // change at every invalidation entry point, independently of the graph's mutation logic.
    Neo4JProxyUser proxy = mock(Neo4JProxyUser.class, invocation -> {
      if (invocation.getMethod().getName().equals("findUserByApiKey")) {
        FolderServerUser snapshot = graph.get();
        if (first.getAndSet(false)) {
          read.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS), "lookup release");
        }
        return snapshot;
      }
      graph.set(after);
      return null;
    });
    UserServiceNeo4j target = new UserServiceNeo4j(proxy, ApiKeyLookupCache.withTtlSeconds(60));
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<CedarUser> overlapping = executor.submit(() -> target.findUserByApiKey(KEY));
      assertTrue(read.await(5, TimeUnit.SECONDS), "old graph snapshot captured");
      mutation.apply(target);
      release.countDown();
      overlapping.get(5, TimeUnit.SECONDS); // An overlapping request may observe its old snapshot.
      for (int i = 0; i < 2; i++) {
        CedarUser result = target.findUserByApiKey(KEY);
        if (after == null) assertNull(result, "revoked credential must stay revoked");
        else {
          assertNotNull(result, "a stale negative must not hide the new credential");
          assertEquals(List.of("current-permission"), result.getPermissions());
        }
      }
      verify(proxy, times(2)).findUserByApiKey(KEY);
    } finally {
      release.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    }
  }

  @ParameterizedTest
  @EnumSource(Mutation.class)
  void uncertainWriteAlsoInvalidatesCachedCredentials(Mutation mutation) {
    AtomicReference<FolderServerUser> graph = new AtomicReference<>(user());
    Neo4JProxyUser proxy = mock(Neo4JProxyUser.class, invocation -> {
      if (invocation.getMethod().getName().equals("findUserByApiKey")) return graph.get();
      graph.set(null);
      throw new IllegalStateException("write committed but completion failed");
    });
    UserServiceNeo4j target = new UserServiceNeo4j(proxy, ApiKeyLookupCache.withTtlSeconds(60));
    assertNotNull(target.findUserByApiKey(KEY));
    assertThrows(IllegalStateException.class, () -> mutation.apply(target));
    assertNull(target.findUserByApiKey(KEY));
  }
}
