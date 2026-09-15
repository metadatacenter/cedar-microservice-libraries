package org.metadatacenter.util.http;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpRequestRetryStrategy;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.fluent.Executor;
import org.apache.hc.client5.http.fluent.Request;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ConnectionClosedException;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.Method;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.OutboundCallClassConfig;
import org.metadatacenter.config.OutboundHttpConfig;
import org.metadatacenter.config.OutboundTimeoutOverride;

import jakarta.ws.rs.core.HttpHeaders;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Objects;

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
 *
 * <p>The values come from {@code http:} in {@code cedar-main.yml}, which {@link #install} hands over
 * once at startup. Until it does, and in a process that never calls it, the defaults on
 * {@link OutboundHttpConfig} apply, so a tool or a test needs no configuration to make a call.
 */
public final class HttpTimeouts {

  /** The kind of outbound call a set of bounds belongs to. */
  public enum CallClass {
    /** One CEDAR service reaching the next, or a nearby dependency, while a user waits. */
    INTERACTIVE,
    /** A call from a job with nobody waiting on it: an import, a reindex, a bulk clone. */
    BATCH,
    /** A call that leaves the estate for a registry CEDAR does not operate. */
    EXTERNAL;

    OutboundCallClassConfig of(OutboundHttpConfig config) {
      return switch (this) {
        case INTERACTIVE -> config.getInteractive();
        case BATCH -> config.getBatch();
        case EXTERNAL -> config.getExternal();
      };
    }
  }

  /**
   * Which configured override an instance follows.
   *
   * <p>A shared instance outlives any one configuration, so it names where its override comes from
   * rather than holding the override itself. An instance derived by {@link #with} holds one
   * directly and names {@code NONE}.
   */
  private enum OverrideSource {
    NONE,
    ARTIFACT_HOP,
    EXTERNAL_AUTHORITIES
  }

  /**
   * The configuration in force, and how many times it has been replaced.
   *
   * <p>A client is built on first use and kept, so the counter is what tells an already-built one
   * that it no longer matches the configuration and has to be replaced. A server installs once at
   * startup; a test process may install repeatedly.
   */
  private static volatile OutboundHttpConfig installed = new OutboundHttpConfig();
  private static volatile OutboundTimeoutOverride artifactHopOverride = new OutboundTimeoutOverride();
  private static volatile OutboundTimeoutOverride externalAuthoritiesOverride = new OutboundTimeoutOverride();
  private static volatile int generation = 0;

  /** A call a user is waiting on: one CEDAR service reaching the next, or a nearby dependency. */
  public static final HttpTimeouts INTERACTIVE = forClass(CallClass.INTERACTIVE, true, OverrideSource.NONE);

  /** A call from a job with nobody waiting on it: an import, a reindex, a bulk clone. */
  public static final HttpTimeouts BATCH = forClass(CallClass.BATCH, true, OverrideSource.NONE);

  /**
   * A call to a registry outside the estate.
   *
   * <p>Separate from the interactive class because the network is not the same network. A connect
   * timeout that is generous for the next process on this host is mean for a cold TLS handshake to
   * a transatlantic one, and reporting a slow registry as an unavailable one is what that produced.
   */
  public static final HttpTimeouts EXTERNAL = forClass(CallClass.EXTERNAL, true, OverrideSource.EXTERNAL_AUTHORITIES);

  // Service credentials must never follow a redirect, including one to another path on the host.
  // These two also carry the artifact hop's own override, the only per-hop value there is today.
  static final HttpTimeouts ARTIFACT_INTERACTIVE = forClass(CallClass.INTERACTIVE, false, OverrideSource.ARTIFACT_HOP);
  static final HttpTimeouts ARTIFACT_BATCH = forClass(CallClass.BATCH, false, OverrideSource.ARTIFACT_HOP);

  /** Anonymous compatibility proxies preserve redirects as responses rather than following them. */
  public static final HttpTimeouts ANONYMOUS_INTERACTIVE = forClass(CallClass.INTERACTIVE, false, OverrideSource.NONE);

  /** Credential-preserving internal reads must not follow a downstream redirect. */
  public static final HttpTimeouts NO_REDIRECT_INTERACTIVE = ANONYMOUS_INTERACTIVE;

  private final CallClass callClass;
  private final boolean followRedirects;
  private final OverrideSource overrideSource;
  private final OutboundTimeoutOverride override;
  private final OutboundCallClassConfig fixedSettings;
  private volatile Client client;

  /** One materialized client, and the configuration generation it was built for. */
  private record Client(int generation, CloseableHttpClient http, Executor executor,
                        Timeout connectTimeout, Timeout responseTimeout) {

    void close() {
      try {
        http.close();
      } catch (IOException ignored) {
        // A pool being replaced cannot fail the call that replaced it.
      }
    }
  }

  private static HttpTimeouts forClass(CallClass callClass, boolean followRedirects,
                                       OverrideSource overrideSource) {
    return new HttpTimeouts(callClass, followRedirects, overrideSource, new OutboundTimeoutOverride(), null);
  }

  private HttpTimeouts(CallClass callClass, boolean followRedirects, OverrideSource overrideSource,
                       OutboundTimeoutOverride override, OutboundCallClassConfig fixedSettings) {
    this.callClass = callClass;
    this.followRedirects = followRedirects;
    this.overrideSource = overrideSource;
    this.override = override;
    this.fixedSettings = fixedSettings;
  }

  /**
   * The seam the tests need: a pool small enough to exhaust deliberately, and values that do not
   * move when a configuration is installed around them.
   */
  HttpTimeouts(int connectMillis, int leaseMillis, int responseMillis, int maxPerRoute, int maxTotal) {
    this(CallClass.INTERACTIVE, true, OverrideSource.NONE, new OutboundTimeoutOverride(),
        new OutboundCallClassConfig(connectMillis, leaseMillis, responseMillis, maxPerRoute, maxTotal));
  }

  /**
   * Hands over the configured bounds for every class of call, and the per-hop overrides.
   *
   * <p>Called once, before the server serves anything. A client already built under the previous
   * configuration is replaced on its next use and its pool closed, which is what lets a test
   * process install more than once.
   */
  public static synchronized void install(CedarConfig config) {
    installed = Objects.requireNonNull(config.getOutboundHttp(), "http: is missing from the configuration");
    artifactHopOverride = config.getServers() == null || config.getServers().getArtifact() == null
        ? new OutboundTimeoutOverride()
        : config.getServers().getArtifact().getTimeouts();
    externalAuthoritiesOverride = config.getExternalAuthorities() == null
        ? new OutboundTimeoutOverride()
        : config.getExternalAuthorities().getTimeouts();
    generation++;
  }

  /**
   * This class of call as one hop or one registry bounds it.
   *
   * <p>The override may change the connect and response timeouts, which are set on each request. It
   * cannot change the lease timeout or the pool, which belong to the class, so the returned bounds
   * share this class's settings for both rather than opening another pool.
   */
  public HttpTimeouts with(OutboundTimeoutOverride hopOverride) {
    if (hopOverride == null || hopOverride.isEmpty()) {
      return this;
    }
    return new HttpTimeouts(callClass, followRedirects, OverrideSource.NONE, hopOverride, fixedSettings);
  }

  /**
   * Executes the request under this class's timeouts and pool. The response body is buffered before
   * the call returns, so the connection is back in the pool by the time the caller reads it.
   */
  public ClassicHttpResponse execute(Request request) throws IOException {
    Client current = client();
    return (ClassicHttpResponse) current.executor().execute(request
            .connectTimeout(current.connectTimeout())
            .responseTimeout(current.responseTimeout()))
        .returnResponse();
  }

  /** The connect timeout in force, for a caller that reports the bound rather than making a call. */
  public Timeout connectTimeout() {
    return client().connectTimeout();
  }

  /** The response timeout in force, for a caller that reports the bound rather than making a call. */
  public Timeout responseTimeout() {
    return client().responseTimeout();
  }

  private Client client() {
    int current = generation;
    Client existing = client;
    if (existing != null && existing.generation() == current) {
      return existing;
    }
    synchronized (this) {
      if (client != null && client.generation() == current) {
        return client;
      }
      Client replaced = client;
      client = build(current);
      if (replaced != null) {
        replaced.close();
      }
      return client;
    }
  }

  private Client build(int forGeneration) {
    OutboundCallClassConfig settings = fixedSettings != null ? fixedSettings : callClass.of(installed);
    OutboundTimeoutOverride inForce = switch (overrideSource) {
      case ARTIFACT_HOP -> artifactHopOverride;
      case EXTERNAL_AUTHORITIES -> externalAuthoritiesOverride;
      case NONE -> override;
    };

    // cedar.test.dependencyTimeoutMillis is deliberately not consulted here. It bounds the graph
    // and database drivers so an outage test reaches the same exception path without thirty seconds
    // of retry first, and cedar-parent states that production drivers keep their own defaults.
    // Folding it into outbound HTTP as well would replace every configured bound in any process
    // that sets it, including the bounds a test is asserting.
    int connectMillis = inForce.getConnectMillis().orElse(settings.getConnectMillis());
    int responseMillis = inForce.getResponseMillis().orElse(settings.getResponseMillis());
    int leaseMillis = settings.getLeaseMillis();

    CloseableHttpClient http = HttpClientBuilder.create()
        .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
            .useSystemProperties()
            .setMaxConnPerRoute(settings.getMaxConnectionsPerRoute())
            .setMaxConnTotal(settings.getMaxConnectionsTotal())
            .setDefaultConnectionConfig(ConnectionConfig.custom()
                .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                .build())
            .build())
        .setDefaultRequestConfig(RequestConfig.custom()
            .setRedirectsEnabled(followRedirects)
            .setConnectionRequestTimeout(Timeout.ofMilliseconds(leaseMillis))
            .build())
        .useSystemProperties()
        .setRetryStrategy(ANSWERLESS_ATTEMPT)
        // Every executor is process-wide. Retaining an upstream cookie here would let one
        // request leave state that an unrelated later request sends back to the same host.
        .disableCookieManagement()
        .evictExpiredConnections()
        .evictIdleConnections(TimeValue.ofMinutes(1))
        .build();

    return new Client(forGeneration, http, Executor.newInstance(http),
        Timeout.ofMilliseconds(connectMillis), Timeout.ofMilliseconds(responseMillis));
  }

  /**
   * Whether the dependency wrote nothing at all, so asking again cannot repeat work it has done.
   *
   * <p>The order of these tests is load-bearing. {@link ConnectTimeoutException} extends
   * {@link SocketTimeoutException}, so a connect timeout and a response timeout are told apart by
   * class rather than by the timeout they share, and a response timeout has to fall through to
   * false: the request arrived, and the server may still be working on it.
   */
  private static boolean answeredNothing(IOException exception) {
    if (exception instanceof ConnectTimeoutException || exception instanceof ConnectException) {
      return true;
    }
    if (exception instanceof NoHttpResponseException || exception instanceof ConnectionClosedException) {
      return true;
    }
    // A peer that resets rather than closing cleanly, which a pooling client meets whenever the far
    // side retires an idle connection between the lease and the write.
    return exception instanceof SocketException && !(exception instanceof SocketTimeoutException);
  }

  /**
   * The outbound failures worth repeating, and the two rules that decide which they are.
   *
   * <p>A response is never repeated, whatever its status. A 503 is a real answer, so the dependency
   * read the request and may have acted on it, and repeating a POST after one can create the same
   * logical resource twice. A response timeout is the same case: the request arrived, and the
   * server may still be working on it, so sending it again risks doing the work twice. Only a
   * failure that proves nothing was answered can be settled by asking again.
   *
   * <p>Which methods may then repeat follows from what the dependency could have done. For a GET,
   * HEAD, OPTIONS or TRACE, an answerless attempt is the end of it. For a PUT or DELETE the
   * dependency cannot have answered, but proving it never acted would need to rule out a server
   * that read the request, did the work and died before writing, and no client can see the
   * difference -- so those repeat only while carrying an {@code If-Match}, which makes the repeat
   * conditional on the state the first attempt expected. A POST has no deduplication key at all and
   * never repeats.
   *
   * <p>A lease timeout is deliberately not treated as answerless, though nothing was answered. It
   * means the pool is saturated, so an immediate repeat queues against the same full pool and
   * doubles the wait a call site was promised. Repeating it needs a request deadline to come out
   * of, and there is none yet.
   */
  private static final HttpRequestRetryStrategy ANSWERLESS_ATTEMPT = new HttpRequestRetryStrategy() {

    @Override
    public boolean retryRequest(HttpRequest request, IOException exception, int execCount, HttpContext context) {
      if (execCount != 1 || !answeredNothing(exception)) {
        return false;
      }
      return switch (Method.normalizedValueOf(request.getMethod())) {
        case GET, HEAD, OPTIONS, TRACE -> true;
        case PUT, DELETE -> request.containsHeader(HttpHeaders.IF_MATCH);
        default -> false;
      };
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
}
