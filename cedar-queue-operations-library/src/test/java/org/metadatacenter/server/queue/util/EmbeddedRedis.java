package org.metadatacenter.server.queue.util;

import redis.embedded.RedisServer;
import redis.embedded.core.ExecutableProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;
import java.net.InetAddress;
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

  private final RedisServer server;
  private final int port;
  private final Path directory;
  private boolean stopped;

  private EmbeddedRedis(RedisServer server, int port, Path directory) {
    this.server = server;
    this.port = port;
    this.directory = directory;
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

  /** Starts on a caller-chosen port, once. */
  static EmbeddedRedis startOn(int port) {
    Path directory = null;
    try {
      // The binary is unpacked into, and the server writes its dump to, a directory of its own, which
      // stop() removes. Left to the library, each start left a redis-<n> directory in the shared
      // temporary directory.
      directory = Files.createTempDirectory("embedded-redis-");
      RedisServer server = RedisServer.newRedisServer()
          .executableProvider(ExecutableProvider.newJarResourceProvider(directory.toFile()))
          .bind("127.0.0.1").port(port).build();
      server.start();
      return new EmbeddedRedis(server, port, directory);
    } catch (IOException e) {
      deleteQuietly(directory);
      throw new IllegalStateException("Could not start the embedded Redis on port " + port, e);
    }
  }

  private static void deleteQuietly(Path directory) {
    if (directory == null) {
      return;
    }
    try (Stream<Path> paths = Files.walk(directory)) {
      paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
    } catch (IOException ignored) {
      // Leaving a directory behind is untidy, not a test failure.
    }
  }

  /**
   * A port free at this instant. Nothing reserves it, so {@link #start()} binds it at once and retries
   * on another if a different process wins that race. It is no unreachable address: something else may
   * listen on it a moment later. A test that needs one uses port 1, which nothing in a test run can bind.
   */
  public static int freePort() {
    try (ServerSocket socket = new ServerSocket(
        0, 50, InetAddress.getByName("127.0.0.1"))) {
      return socket.getLocalPort();
    } catch (IOException e) {
      throw new IllegalStateException("Could not find a free port for the embedded Redis", e);
    }
  }

  /** The directory the server runs from, which {@link #stop()} removes. */
  Path directory() {
    return directory;
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
    } finally {
      deleteQuietly(directory);
    }
  }

  @Override
  public void close() {
    stop();
  }
}
