package org.metadatacenter.server.queue.util;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.function.IntSupplier;

/**
 * A real redis-server for the queue tests, in place of the live cache. It runs on a free port
 * picked at start, so it can never collide with, or write into, a developer's own Redis, and the
 * binary is bundled rather than downloaded, so the tests need no network and no Docker.
 * <p>
 * A real server rather than a stub because what these tests are checking is Redis behaviour:
 * whether a blocking pop actually blocks and returns the queue it popped from, whether list order
 * survives a round trip, whether a pool recovers after the server goes away and comes back. A
 * reimplementation would only prove the reimplementation agrees with itself.
 */
public final class EmbeddedRedis implements AutoCloseable {

  private static final int START_ATTEMPTS = 5;
  private static final long RETRY_PAUSE_MILLIS = 100;

  private final RedisServer server;
  private final int port;
  private boolean stopped;

  private EmbeddedRedis(RedisServer server, int port) {
    this.server = server;
    this.port = port;
  }

  public static EmbeddedRedis start() {
    return start(EmbeddedRedis::freePort);
  }

  static EmbeddedRedis start(IntSupplier portSupplier) {
    IllegalStateException lastFailure = null;
    for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
      try {
        return startOn(portSupplier.getAsInt());
      } catch (IllegalStateException e) {
        lastFailure = e;
      }
    }
    throw new IllegalStateException("Could not start the embedded Redis after "
        + START_ATTEMPTS + " attempts", lastFailure);
  }

  /**
   * Starts on a caller-chosen port, which is what lets a test stop the server and bring it back at
   * the same address - the shape of a real outage, and the only way to check that a service
   * recovers from one rather than merely failing gracefully.
   */
  public static EmbeddedRedis startOn(int port) {
    try {
      RedisServer server = RedisServer.newRedisServer().port(port).build();
      server.start();
      return new EmbeddedRedis(server, port);
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the embedded Redis on port " + port, e);
    }
  }

  /**
   * Starts again on a port this helper has just released. Unlike {@link #start()} it cannot move to
   * another port, because coming back at the same address is the whole point of a restart, so it
   * waits and retries instead: a server that has only just stopped can hold its listening socket a
   * moment longer.
   */
  public static EmbeddedRedis restartOn(int port) {
    return restartOn(port, EmbeddedRedis::startOn);
  }

  static EmbeddedRedis restartOn(int port, Starter starter) {
    IllegalStateException lastFailure = null;
    for (int attempt = 1; attempt <= START_ATTEMPTS; attempt++) {
      try {
        return starter.startOnce(port);
      } catch (IllegalStateException e) {
        lastFailure = e;
        pauseBeforeRetry();
      }
    }
    throw new IllegalStateException("Could not restart the embedded Redis on port " + port
        + " after " + START_ATTEMPTS + " attempts", lastFailure);
  }

  /** One attempt at starting on a given port, so a test can drive the retry without a real race. */
  @FunctionalInterface
  interface Starter {
    EmbeddedRedis startOnce(int port);
  }

  private static void pauseBeforeRetry() {
    try {
      Thread.sleep(RETRY_PAUSE_MILLIS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting for the embedded Redis port", e);
    }
  }

  /**
   * A port free at this instant. Nothing reserves it, so a caller should bind it promptly. The
   * ordinary {@link #start()} path retries if another embedded dependency wins that race; callers
   * that only need a deliberately unreachable address can use this value directly.
   */
  public static int freePort() {
    try (ServerSocket socket = new ServerSocket(0)) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Could not find a free port for the embedded Redis", e);
    }
  }

  public int port() {
    return port;
  }

  /**
   * Stops the server, and does nothing if it is already stopped. A test that stops the server
   * itself, to reproduce an outage, still stops it again in a finally block; without this that
   * second call would raise a failure of its own and hide whichever one ended the test.
   */
  public void stop() {
    if (stopped) {
      return;
    }
    stopped = true;
    try {
      server.stop();
    } catch (IOException e) {
      throw new IllegalStateException("Could not stop the embedded Redis on port " + port, e);
    }
  }

  @Override
  public void close() {
    stop();
  }
}
