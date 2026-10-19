package org.metadatacenter.server.search.util;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.server.queue.util.EmbeddedRedis;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Redis command boundaries plus bounded thread races, with no sleeps or live services. */
@Tag("integration")
class IndexRebuildConcurrencyTest {
  private static EmbeddedRedis redis;
  private static JedisPool pool;

  @BeforeAll static void start() {
    redis = EmbeddedRedis.start();
    pool = new JedisPool("127.0.0.1", redis.port());
  }

  @AfterAll static void stop() {
    if (pool != null) pool.close();
    if (redis != null) redis.close();
    IndexRebuildRegistry.reset();
  }

  @AfterEach void resetRegistry() { IndexRebuildRegistry.reset(); }

  @BeforeEach void clear() {
    try (var jedis = pool.getResource()) { jedis.flushDB(); }
  }

  /**
   * Schedule another client immediately after the first Redis command. This exercises both an
   * atomic implementation and the former multi-command one; assertFired prevents vacuous success
   * if the implementation changes its Redis entry point. The callback uses a separate connection.
   */
  private static final class Boundary {
    private final AtomicReference<Runnable> next = new AtomicReference<>();
    private final JedisPool connections = mock(JedisPool.class);

    Boundary() {
      when(connections.getResource()).thenAnswer(ignored -> new Jedis("127.0.0.1", redis.port()) {
        @Override public String get(String key) {
          String result = super.get(key); fire(); return result;
        }
        @Override public long del(String... keys) {
          long result = super.del(keys); fire(); return result;
        }
        @Override public long sadd(String key, String... members) {
          long result = super.sadd(key, members); fire(); return result;
        }
        @Override public Set<String> smembers(String key) {
          Set<String> result = super.smembers(key); fire(); return result;
        }
        @Override public Object eval(String script, List<String> keys, List<String> args) {
          Object result = super.eval(script, keys, args); fire(); return result;
        }
      });
    }
    void fire() { Runnable action = next.getAndSet(null); if (action != null) action.run(); }
    void arm(Runnable action) { next.set(action); }
    void assertFired() { assertNull(next.get(), "the command-boundary schedule must actually execute"); }
    RedisIndexRebuildStore store() { return new RedisIndexRebuildStore(connections); }
  }

  @Test void oldCleanupCannotEraseAReplacementStartedAtItsFirstCommandBoundary() {
    var boundary = new Boundary();
    var old = boundary.store();
    var replacement = new RedisIndexRebuildStore(pool);
    old.begin("old");
    boundary.arm(() -> { replacement.begin("new"); replacement.recordWrite("fresh"); });
    old.end("old");
    boundary.assertFired();
    assertEquals(Optional.of("new"), new RedisIndexRebuildStore(pool).inProgressIndex());
    assertEquals(Set.of("fresh"), replacement.written());
  }

  @Test void startingAReplacementCannotExposeHalfClearedState() {
    var boundary = new Boundary();
    var old = boundary.store();
    var replacement = new RedisIndexRebuildStore(pool);
    old.begin("previous");
    boundary.arm(() -> { replacement.begin("new"); replacement.recordDelete("fresh"); });
    old.begin("old");
    boundary.assertFired();
    assertEquals(Optional.of("new"), new RedisIndexRebuildStore(pool).inProgressIndex());
    assertEquals(Set.of("fresh"), replacement.deleted());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void oppositeUpdatesAtACommandBoundaryCannotLoseAnIdentifier(boolean writeFirst) {
    var boundary = new Boundary();
    var first = boundary.store();
    var second = new RedisIndexRebuildStore(pool);
    first.begin("active"); // Warm first's name cache before arming the mutation boundary.
    boundary.arm(() -> { if (writeFirst) second.recordDelete("id"); else second.recordWrite("id"); });
    if (writeFirst) first.recordWrite("id"); else first.recordDelete("id");
    boundary.assertFired();
    assertEquals(Set.of("id"), writeFirst ? first.deleted() : first.written());
    assertTrue((writeFirst ? first.written() : first.deleted()).isEmpty());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void cachedOldWritersCannotCreateTrackingAfterEndOrInsideAReplacement(boolean replace) {
    var old = new RedisIndexRebuildStore(pool);
    var current = new RedisIndexRebuildStore(pool);
    old.begin("old");
    current.end("old");
    if (replace) { current.begin("new"); current.recordWrite("fresh"); }
    old.recordWrite("late-write");
    old.recordDelete("late-delete");
    old.end("old");
    assertEquals(replace ? Optional.of("new") : Optional.empty(), new RedisIndexRebuildStore(pool).inProgressIndex());
    assertEquals(replace ? Set.of("fresh") : Set.of(), current.written());
    assertTrue(current.deleted().isEmpty());
  }

  @Test void generationIsRecheckedWhenReplacementStartsAfterTheNameRead() {
    var owner = new RedisIndexRebuildStore(pool);
    owner.begin("old");
    var boundary = new Boundary();
    var late = boundary.store(); // Its first command reads the old name, before the replacement.
    boundary.arm(() -> { owner.begin("new"); owner.recordWrite("fresh"); });
    late.recordDelete("late");
    boundary.assertFired();
    assertEquals(Set.of("fresh"), owner.written());
    assertTrue(owner.deleted().isEmpty());
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void concurrentOppositeUpdatesRetainExactlyOneMembership(boolean useRedis) throws Exception {
    IndexRebuildStore owner = useRedis ? new RedisIndexRebuildStore(pool) : new InMemoryIndexRebuildStore();
    owner.begin("active");
    var executor = Executors.newFixedThreadPool(4);
    var ready = new CyclicBarrier(4);
    try {
      var work = new ArrayList<Future<?>>();
      for (int worker = 0; worker < 4; worker++) {
        final boolean write = worker % 2 == 0;
        IndexRebuildStore client = useRedis ? new RedisIndexRebuildStore(pool) : owner;
        work.add(executor.submit(() -> {
          ready.await(10, TimeUnit.SECONDS);
          for (int round = 0; round < (useRedis ? 100 : 10_000); round++) {
            String id = "id-" + round % 8;
            if (write) client.recordWrite(id); else client.recordDelete(id);
          }
          return null;
        }));
      }
      for (var result : work) result.get(20, TimeUnit.SECONDS);
      var touched = new HashSet<>(owner.written());
      touched.addAll(owner.deleted());
      assertEquals(Set.of("id-0", "id-1", "id-2", "id-3", "id-4", "id-5", "id-6", "id-7"), touched);
      assertTrue(Collections.disjoint(owner.written(), owner.deleted()));
    } finally {
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void reachingTheCapStillAllowsExistingIdentifiersToChangeSides(boolean useRedis) {
    IndexRebuildStore store = useRedis ? new RedisIndexRebuildStore(pool) : new InMemoryIndexRebuildStore();
    store.begin("active");
    if (useRedis) {
      try (var jedis = pool.getResource()) {
        var ids = new String[RedisIndexRebuildStore.MAX_TRACKED];
        for (int i = 0; i < ids.length; i++) ids[i] = "id-" + i;
        jedis.sadd(RedisIndexRebuildStore.KEY_WRITTEN, ids);
      }
    } else {
      for (int i = 0; i < InMemoryIndexRebuildStore.MAX_TRACKED; i++) store.recordWrite("id-" + i);
    }
    store.recordWrite("overflow");
    store.recordDelete("id-0");
    assertTrue(store.overflowed());
    assertFalse(store.written().contains("overflow"));
    assertFalse(store.written().contains("id-0"));
    assertEquals(Set.of("id-0"), store.deleted());
    store.recordWrite("id-0");
    assertTrue(store.deleted().isEmpty());
    assertTrue(store.written().contains("id-0"));
  }

  @ParameterizedTest @ValueSource(booleans = {false, true})
  void snapshotReadersCannotLoseAnIdMovingBetweenSets(boolean countAdjustment) {
    var boundary = new Boundary();
    var reader = boundary.store();
    var writer = new RedisIndexRebuildStore(pool);
    reader.begin("active");
    reader.recordDelete("id");
    IndexRebuildRegistry.install(reader);
    boundary.arm(() -> writer.recordWrite("id"));
    if (countAdjustment) {
      // Before the write this contributes -1; after it contributes 0. The snapshot returned by
      // the first Redis read is before the callback, so its answer must describe that instant.
      assertEquals(-1, IndexRebuildRegistry.expectedCountAdjustment(Set.of("id")));
    } else {
      assertEquals(Set.of("id"), IndexRebuildRegistry.liveTouched());
    }
    boundary.assertFired();
    assertEquals(Set.of("id"), writer.written());
    assertTrue(writer.deleted().isEmpty());
  }

  @Test void activityRefreshesBothTrackingSetsAlongWithTheIndex() {
    var store = new RedisIndexRebuildStore(pool);
    store.begin("active");
    store.recordWrite("written");
    store.recordDelete("deleted");
    try (var jedis = pool.getResource()) {
      for (String key : List.of(RedisIndexRebuildStore.KEY_INDEX,
          RedisIndexRebuildStore.KEY_WRITTEN, RedisIndexRebuildStore.KEY_DELETED)) jedis.expire(key, 60);
      store.recordWrite("another");
      for (String key : List.of(RedisIndexRebuildStore.KEY_INDEX,
          RedisIndexRebuildStore.KEY_WRITTEN, RedisIndexRebuildStore.KEY_DELETED)) {
        assertTrue(jedis.ttl(key) >= RedisIndexRebuildStore.KEY_TTL_SECONDS - 1,
            "an active rebuild must not let its quieter tracking set expire: " + key);
      }
    }
  }

  @Test void failedRedisOperationsRemainBestEffort() {
    var unavailable = mock(JedisPool.class);
    when(unavailable.getResource()).thenThrow(new IllegalStateException("injected outage"));
    var store = new RedisIndexRebuildStore(unavailable);
    assertDoesNotThrow(() -> {
      store.begin("active"); store.recordWrite("id"); store.recordDelete("id"); store.end("active");
    });
    assertTrue(store.inProgressIndex().isEmpty());
    assertTrue(store.written().isEmpty());
    assertTrue(store.deleted().isEmpty());
    assertEquals(new IndexRebuildStore.Snapshot(Set.of(), Set.of()), store.snapshot());
  }
}
