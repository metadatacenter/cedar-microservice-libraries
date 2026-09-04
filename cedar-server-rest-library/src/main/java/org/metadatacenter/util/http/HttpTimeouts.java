package org.metadatacenter.util.http;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.constant.HttpConnectionConstants;

import java.io.IOException;

/**
 * The class of outbound HTTP call a request belongs to, carrying the three timeouts and the
 * connection pool it runs under.
 *
 * <p>The lease timeout is why this type exists. A fluent {@link Request} accepts a connect and a
 * response timeout but offers no setter for the third one, the time a caller waits for a connection
 * out of the pool, and the only way to reach it is the client's own default request configuration.
 * A fluent request copies that configuration before applying its two values, so setting the lease
 * on the client and the other two per request leaves all three in force.
 *
 * <p>A pool per class is the second reason. Every call in a service shared one pool of 100
 * connections per route, so a bulk job could hold every connection an interactive request needed.
 * Separate pools confine that interference to the class of call that caused it.
 */
public final class HttpTimeouts {

  /** A call a user is waiting on: one CEDAR service reaching the next, or a nearby dependency. */
  public static final HttpTimeouts INTERACTIVE = new HttpTimeouts(
      HttpConnectionConstants.CONNECTION_TIMEOUT,
      HttpConnectionConstants.CONNECTION_LEASE_TIMEOUT,
      HttpConnectionConstants.SOCKET_TIMEOUT,
      100, 200);

  /** A call from a job with nobody waiting on it: an import, a reindex, a bulk clone. */
  public static final HttpTimeouts BATCH = new HttpTimeouts(
      HttpConnectionConstants.BATCH_CONNECTION_TIMEOUT,
      HttpConnectionConstants.BATCH_CONNECTION_LEASE_TIMEOUT,
      HttpConnectionConstants.BATCH_SOCKET_TIMEOUT,
      10, 20);

  /**
   * The only outbound failure worth repeating: a pooled connection the dependency had already
   * closed, which produces no response at all.
   *
   * <p>Two failures look alike from a call site and are not alike. A 503 is a real answer, so the
   * dependency read the request and may have acted on it, and repeating a POST after one can create
   * the same logical resource twice; a response is therefore never retried, whatever its status. A
   * {@link NoHttpResponseException} out of the response-header read is the other case. The
   * dependency wrote nothing, which is what a connection closed before the request was read looks
   * like, and a pooling client meets it whenever a peer retires an idle connection between the
   * lease and the write. Repeating an idempotent request there costs a connection and settles it.
   *
   * <p>The restriction to idempotent methods is what keeps the first paragraph true of this case
   * too. The dependency cannot have answered, but proving it never acted would need to rule out a
   * server that read the request, did the work and died before writing, and no client can see the
   * difference. So GET, HEAD, PUT, DELETE, OPTIONS and TRACE recover, and POST and PATCH surface
   * the failure to a call site that knows whether repeating is safe.
   */
  private static final HttpRequestRetryStrategy ANSWERLESS_CONNECTION = new HttpRequestRetryStrategy() {

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context) {
      return execCount == 1
          && exception instanceof NoHttpResponseException
          && Method.isIdempotent(request.getMethod());
    }

    @Override
    public boolean retryRequest(HttpResponse response, int execCount, HttpContext context) {
      return false;
    }

    @Override
    public TimeValue getRetryInterval(HttpResponse response, int execCount, HttpContext context) {
      return TimeValue.ZERO_MILLISECONDS;
    }
  };

  private final Timeout connectTimeout;
  private final Timeout responseTimeout;
  private final Executor executor;

  /**
   * The seam a configured per-hop timeout would arrive through. It is visible to the tests, which
   * need a pool small enough to exhaust deliberately, and to nothing else until a call site has a
   * reason to name its own values.
   */
  HttpTimeouts(int connectMillis, int leaseMillis, int responseMillis, int maxPerRoute, int maxTotal) {
    this.connectTimeout = Timeout.ofMilliseconds(connectMillis);
    this.responseTimeout = Timeout.ofMilliseconds(responseMillis);
    this.executor = Executor.newInstance(HttpClientBuilder.create()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .useSystemProperties()
            .setMaxConnPerRoute(maxPerRoute)
            .setMaxConnTotal(maxTotal)
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                .build())
            .build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(leaseMillis))
            .build())
        .useSystemProperties()
        .setRetryStrategy(ANSWERLESS_CONNECTION)
        .evictExpiredConnections()
        .evictIdleConnections(TimeValue.ofMinutes(1))
        .build());
  }

  /**
   * Executes the request under this class's timeouts and pool. The response body is buffered before
   * the call returns, so the connection is back in the pool by the time the caller reads it.
   */
  public ClassicHttpResponse execute(Request request) throws IOException {
    return (ClassicHttpResponse) executor.execute(request
            .connectTimeout(connectTimeout)
            .responseTimeout(responseTimeout))
        .returnResponse();
  }
}
