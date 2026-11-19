package org.metadatacenter.util.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.BindException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The proxy forwards until an outage begins, then resets the connections it holds and every new one
 * while keeping its port, and forwards again once the outage ends. An outage test that used a proxy
 * whose failure did nothing would see a healthy dependency and pass for the wrong reason.
 */
@Tag("integration")
class TcpFaultProxyTest {

  @Test
  void anOutageResetsOpenAndNewConnectionsAndKeepsThePortUntilItEnds() throws Exception {
    try (ServerSocket target = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
         TcpFaultProxy proxy = TcpFaultProxy.start("127.0.0.1", target.getLocalPort())) {
      Thread echo = new Thread(() -> echoEveryConnection(target), "tcp-fault-proxy-test-echo");
      echo.setDaemon(true);
      echo.start();

      try (Socket open = connect(proxy)) {
        assertEchoes(open, 37);

        proxy.failConnections();

        assertTrue(endedOrReset(open), "a connection open when the outage began must end");
        assertTrue(aNewConnectionIsNotForwarded(proxy), "a connection made during the outage must not be forwarded");
        assertThrows(BindException.class, () -> {
          try (ServerSocket ignored = new ServerSocket(proxy.port(), 1, InetAddress.getByName("127.0.0.1"))) {
            // The proxy must retain this exact address until close().
          }
        });
      }

      proxy.restoreConnections();

      try (Socket after = connect(proxy)) {
        assertEchoes(after, 43);
      }
    }
  }

  private static Socket connect(TcpFaultProxy proxy) throws IOException {
    Socket socket = new Socket("127.0.0.1", proxy.port());
    socket.setSoTimeout(5_000);
    return socket;
  }

  /**
   * True when a connection made now ends or resets without forwarding. Under load the proxy can accept
   * and reset the connection before connect returns, which also forwards nothing. A refused connection
   * means the listener is gone, so it is thrown rather than counted.
   */
  private static boolean aNewConnectionIsNotForwarded(TcpFaultProxy proxy) throws IOException {
    Socket during;
    try {
      during = connect(proxy);
    } catch (ConnectException refused) {
      throw refused;
    } catch (SocketException resetWhileConnecting) {
      return true;
    }
    try (during) {
      try {
        during.getOutputStream().write(41);
      } catch (SocketException alreadyReset) {
        // The reset can arrive before the write; either way nothing is forwarded.
      }
      return endedOrReset(during);
    }
  }

  private static void assertEchoes(Socket socket, int value) throws IOException {
    socket.getOutputStream().write(value);
    assertEquals(value, socket.getInputStream().read(), "the proxy should forward to the target and back");
  }

  /** True when the peer ended or reset the connection; a forwarded echo or a timeout is false. */
  private static boolean endedOrReset(Socket socket) {
    try {
      InputStream in = socket.getInputStream();
      return in.read() == -1;
    } catch (SocketException e) {
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private static void echoEveryConnection(ServerSocket target) {
    while (!target.isClosed()) {
      try {
        Socket connection = target.accept();
        Thread pump = new Thread(() -> {
          try (connection) {
            int value;
            while ((value = connection.getInputStream().read()) != -1) {
              connection.getOutputStream().write(value);
            }
          } catch (IOException ignored) {
            // A reset from the proxy ends the echo.
          }
        }, "tcp-fault-proxy-test-echo-connection");
        pump.setDaemon(true);
        pump.start();
      } catch (IOException e) {
        return;
      }
    }
  }
}
