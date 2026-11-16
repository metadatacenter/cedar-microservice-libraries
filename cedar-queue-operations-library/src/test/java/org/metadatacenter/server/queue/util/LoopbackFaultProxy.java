package org.metadatacenter.server.queue.util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A loopback TCP proxy that keeps its port while a test takes the dependency behind it away and brings
 * it back. Stopping a server and restarting it on its former port assumes nothing else takes the port
 * in between, which a parallel build does not guarantee.
 *
 * <p>The same proxy is {@code TcpFaultProxy} in cedar-test-support-library, which this module cannot
 * use: that library depends on this one.
 */
final class LoopbackFaultProxy implements AutoCloseable {

  private static final int CONNECT_TIMEOUT_MILLIS = 2_000;

  private final InetSocketAddress target;
  private final ServerSocket listener;
  private final ExecutorService workers;
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean failing = new AtomicBoolean();

  LoopbackFaultProxy(int targetPort) throws IOException {
    target = new InetSocketAddress("127.0.0.1", targetPort);
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress("127.0.0.1", 0));
    workers = Executors.newCachedThreadPool(task -> {
      Thread thread = new Thread(task, "queue-test-fault-proxy");
      thread.setDaemon(true);
      return thread;
    });
    workers.submit(this::accept);
  }

  int port() {
    return listener.getLocalPort();
  }

  /** Resets every open connection and every one accepted until {@link #restoreConnections()}. */
  void failConnections() {
    failing.set(true);
    for (Socket connection : connections) {
      resetAndClose(connection);
    }
  }

  void restoreConnections() {
    failing.set(false);
  }

  private void accept() {
    while (!listener.isClosed()) {
      try {
        Socket client = listener.accept();
        if (failing.get()) {
          resetAndClose(client);
        } else {
          connect(client);
        }
      } catch (IOException e) {
        if (!listener.isClosed()) {
          throw new IllegalStateException("The fault proxy could not accept a connection", e);
        }
      }
    }
  }

  private void connect(Socket client) {
    Socket upstream = new Socket();
    try {
      upstream.connect(target, CONNECT_TIMEOUT_MILLIS);
      connections.add(client);
      connections.add(upstream);
      if (failing.get()) {
        resetAndClose(client);
        resetAndClose(upstream);
        return;
      }
      workers.submit(() -> copy(client, upstream));
      workers.submit(() -> copy(upstream, client));
    } catch (IOException e) {
      resetAndClose(client);
      resetAndClose(upstream);
    }
  }

  private void copy(Socket source, Socket destination) {
    try {
      source.getInputStream().transferTo(destination.getOutputStream());
    } catch (IOException ignored) {
      // A reset during an outage is how both pumps end.
    } finally {
      resetAndClose(source);
      resetAndClose(destination);
    }
  }

  private void resetAndClose(Socket socket) {
    connections.remove(socket);
    try {
      socket.setSoLinger(true, 0);
    } catch (SocketException ignored) {
      // Already closed by the other pump.
    }
    try {
      socket.close();
    } catch (IOException ignored) {
      // Closing is idempotent here.
    }
  }

  @Override
  public void close() throws IOException {
    try {
      listener.close();
    } finally {
      for (Socket connection : connections) {
        resetAndClose(connection);
      }
      workers.shutdownNow();
    }
  }
}
