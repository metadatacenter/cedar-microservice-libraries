package org.metadatacenter.util.http;

import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.core5.http.ConnectionRequestTimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.context.CedarRequestContext;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * A caller that finds the connection pool full has to be refused quickly. Apache's own default for
 * that wait is three minutes, taken before either the connect or the response timeout is consulted,
 * which is how a hop nominally bounded at 21 seconds held a worker for 201.
 *
 * <p>Both tests exhaust a pool of one against a server that accepts a connection and then says
 * nothing, so the second caller can only be waiting on the lease.
 */
class HttpTimeoutsTest {

  private static final int LEASE_MILLIS = 200;
  private static final int RESPONSE_MILLIS = 5_000;
  private static final long LEASE_CEILING_MILLIS = 2_000;

  private final CedarRequestContext context = mock(CedarRequestContext.class);

  private ServerSocket server;
  private final List<Socket> accepted = new CopyOnWriteArrayList<>();
  private ExecutorService threads;
  private CountDownLatch connected;
  private HttpTimeouts timeouts;
  private String url;

  @BeforeEach
  void startSilentServer() throws IOException {
    server = new ServerSocket(0);
    threads = Executors.newFixedThreadPool(2);
    connected = new CountDownLatch(1);
    timeouts = new HttpTimeouts(1_000, LEASE_MILLIS, RESPONSE_MILLIS, 1, 1);
    url = "http://127.0.0.1:" + server.getLocalPort() + "/dependency";
    threads.submit(() -> {
      while (!server.isClosed()) {
        accepted.add(server.accept());
        connected.countDown();
      }
      return null;
    });
  }

  @AfterEach
  void stopSilentServer() throws IOException {
    server.close();
    for (Socket socket : accepted) {
      socket.close();
    }
    threads.shutdownNow();
  }

  @Test
  void aFullPoolFailsTheNextCallerOnTheLeaseTimeout() throws InterruptedException {
    occupyTheOnlyConnection();

    long start = System.nanoTime();
    assertThrows(ConnectionRequestTimeoutException.class,
        () -> timeouts.execute(Request.get(url)));
    assertWaitedNoLongerThanTheLease(start);
  }

  @Test
  void aFullPoolReachesTheCallerAsServiceUnavailable() throws InterruptedException {
    occupyTheOnlyConnection();

    long start = System.nanoTime();
    CedarDependencyUnavailableException exception = assertThrows(CedarDependencyUnavailableException.class,
        () -> ProxyUtil.proxyGet(url, context, timeouts));
    assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, exception.getErrorPack().getStatus());
    assertWaitedNoLongerThanTheLease(start);
  }

  /**
   * Takes the pool's single connection with a request the silent server never answers, and returns
   * only once the server has accepted it, so the next caller is certainly queueing for the lease
   * rather than racing the first request's connect.
   */
  private void occupyTheOnlyConnection() throws InterruptedException {
    threads.submit(() -> timeouts.execute(Request.get(url)));
    assertTrue(connected.await(5, TimeUnit.SECONDS), "the silent server never accepted a connection");
  }

  private void assertWaitedNoLongerThanTheLease(long startNanos) {
    long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
    assertTrue(elapsedMillis < LEASE_CEILING_MILLIS,
        "waited " + elapsedMillis + " ms for a connection the pool could not give");
  }
}
