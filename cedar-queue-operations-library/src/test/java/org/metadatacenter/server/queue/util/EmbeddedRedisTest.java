package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@Tag("integration")
class EmbeddedRedisTest {

  @Test
  void retriesWithAnotherPortWhenTheFirstCandidateWasTaken() throws Exception {
    try (ServerSocket occupied = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"))) {
      AtomicInteger attempts = new AtomicInteger();

      try (EmbeddedRedis redis = EmbeddedRedis.start(() ->
          attempts.getAndIncrement() == 0 ? occupied.getLocalPort() : EmbeddedRedis.freePort())) {
        assertEquals(2, attempts.get());
        assertNotEquals(occupied.getLocalPort(), redis.port());
      }
    }
  }

  /** A test reproducing an outage stops the server itself, then stops it again in a finally block. */
  @Test
  void stoppingTwiceIsNotAFailure() {
    EmbeddedRedis redis = EmbeddedRedis.start();

    redis.stop();

    assertDoesNotThrow(redis::stop, "the second stop must not raise a failure of its own");
  }
}
