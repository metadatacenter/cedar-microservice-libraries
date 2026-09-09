package org.metadatacenter.util.test;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.BindException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TcpFaultProxyTest {

  @Test
  void failureKeepsTheLoopbackPortOwnedWhileResettingConnections() throws Exception {
    try (ServerSocket target = new ServerSocket(
        0, 1, InetAddress.getByName("127.0.0.1"));
        TcpFaultProxy proxy = TcpFaultProxy.start("127.0.0.1", target.getLocalPort())) {
      Thread echo = new Thread(() -> echoOnce(target), "tcp-fault-proxy-test-echo");
      echo.setDaemon(true);
      echo.start();

      try (Socket client = new Socket("127.0.0.1", proxy.port())) {
        client.getOutputStream().write(37);
        assertEquals(37, client.getInputStream().read());
        proxy.failConnections();
      }

      assertThrows(BindException.class, () -> {
        try (ServerSocket ignored = new ServerSocket(
            proxy.port(), 1, InetAddress.getByName("127.0.0.1"))) {
          // The proxy must retain this exact address until close().
        }
      });
    }
  }

  private static void echoOnce(ServerSocket target) {
    try (Socket connection = target.accept()) {
      int value = connection.getInputStream().read();
      connection.getOutputStream().write(value);
    } catch (IOException e) {
      throw new IllegalStateException("Echo fixture failed", e);
    }
  }
}
