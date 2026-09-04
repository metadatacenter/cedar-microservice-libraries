package org.metadatacenter.server.queue.util;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

  /**
   * A restart cannot take the other way out. Moving to a free port would defeat what the outage
   * test checks, so it keeps asking for the address it was given.
   */
  @Test
  void aRestartKeepsAskingForTheSamePort() {
    List<Integer> requested = new ArrayList<>();
    int port = EmbeddedRedis.freePort();

    try (EmbeddedRedis redis = EmbeddedRedis.restartOn(port, requestedPort -> {
      requested.add(requestedPort);
      if (requested.size() < 3) {
        throw new IllegalStateException("the port is still held");
      }
      return EmbeddedRedis.start();
    })) {
      assertEquals(List.of(port, port, port), requested,
          "every attempt must ask for the address the restart was given");
    }
  }

  @Test
  void aRestartGivesUpRatherThanWaitingForAPortThatNeverComesBack() {
    IllegalStateException failure = assertThrows(IllegalStateException.class,
        () -> EmbeddedRedis.restartOn(EmbeddedRedis.freePort(), port -> {
          throw new IllegalStateException("the port is still held");
        }));

    assertTrue(failure.getMessage().contains("after 5 attempts"), failure.getMessage());
  }

  /** A test reproducing an outage stops the server itself, then stops it again in a finally block. */
  @Test
  void stoppingTwiceIsNotAFailure() {
    EmbeddedRedis redis = EmbeddedRedis.start();

    redis.stop();

    assertDoesNotThrow(redis::stop, "the second stop must not raise a failure of its own");
  }
}
