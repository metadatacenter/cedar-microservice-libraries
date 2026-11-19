package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  /** A stopped server leaves no directory behind in the shared temporary directory. */
  @Test
  void stoppingRemovesTheServersDirectory() {
    EmbeddedRedis redis = EmbeddedRedis.start();
    java.nio.file.Path directory = redis.directory();
    assertTrue(java.nio.file.Files.isDirectory(directory), "the server runs from a directory of its own");

    redis.stop();

    assertFalse(java.nio.file.Files.exists(directory), directory + " was left behind");
  }
}
