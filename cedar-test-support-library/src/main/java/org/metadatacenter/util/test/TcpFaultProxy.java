package org.metadatacenter.util.test;

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
 * A loopback-only TCP proxy whose listener remains owned while a test injects an outage.
 *
 * <p>Stopping an embedded dependency and then treating its former port as unavailable leaves a
 * race: another process can own that port before the client reaches it. This proxy keeps the exact
 * {@code 127.0.0.1:port} tuple bound for its whole lifetime. Before {@link #failConnections()} it
 * forwards bytes to the dependency; afterwards it resets existing and newly accepted connections.
 */
public final class TcpFaultProxy implements AutoCloseable {

  private static final int CONNECT_TIMEOUT_MILLIS = 2_000;

  private final InetSocketAddress target;
  private final ServerSocket listener;
  private final ExecutorService workers;
  private final Set<Socket> connections = ConcurrentHashMap.newKeySet();
  private final AtomicBoolean failing = new AtomicBoolean();
  private final Thread acceptor;

  private TcpFaultProxy(String targetHost, int targetPort) throws IOException {
    target = new InetSocketAddress(targetHost, targetPort);
    listener = new ServerSocket();
    listener.bind(new InetSocketAddress("127.0.0.1", 0));
    workers = Executors.newCachedThreadPool(task -> {
      Thread thread = new Thread(task, "cedar-test-tcp-proxy-worker");
      thread.setDaemon(true);
      return thread;
    });
    acceptor = new Thread(this::accept, "cedar-test-tcp-proxy-acceptor");
    acceptor.setDaemon(true);
    acceptor.start();
  }

  public static TcpFaultProxy start(String targetHost, int targetPort) {
    try {
      return new TcpFaultProxy(targetHost, targetPort);
    } catch (IOException e) {
      throw new IllegalStateException("Could not start the loopback TCP fault proxy", e);
    }
  }

  public int port() {
    return listener.getLocalPort();
  }

  /** Keep the listener bound, but reset every current and future client connection. */
  public void failConnections() {
    failing.set(true);
    for (Socket connection : connections) {
      resetAndClose(connection);
    }
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
      } catch (SocketException e) {
        if (!listener.isClosed()) {
          throw new IllegalStateException("TCP fault proxy listener failed", e);
        }
      } catch (IOException e) {
        if (!listener.isClosed()) {
          throw new IllegalStateException("TCP fault proxy could not accept a connection", e);
        }
      }
    }
  }

  private void connect(Socket client) {
    Socket upstream = new Socket();
    try {
      upstream.connect(target, CONNECT_TIMEOUT_MILLIS);
      if (failing.get()) {
        resetAndClose(client);
        resetAndClose(upstream);
        return;
      }
      connections.add(client);
      connections.add(upstream);
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
      // Closing either half during failure injection is the expected way both pumps end.
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
      // The peer or the other pump may already have closed it.
    }
    try {
      socket.close();
    } catch (IOException ignored) {
      // Closing is best-effort and idempotent here.
    }
  }

  @Override
  public void close() {
    try {
      listener.close();
    } catch (IOException e) {
      throw new IllegalStateException("Could not stop the loopback TCP fault proxy", e);
    } finally {
      for (Socket connection : connections) {
        resetAndClose(connection);
      }
      workers.shutdownNow();
    }
  }
}
