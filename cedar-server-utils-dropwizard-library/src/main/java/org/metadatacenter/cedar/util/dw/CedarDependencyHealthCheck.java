package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.health.HealthCheck;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * One dependency of one server, probed on every health call.
 *
 * <p>Two outcomes are possible when a probe fails, and choosing between them is the whole design
 * decision. {@link #gating} is for a dependency the server cannot serve its own requests without:
 * its failure makes the server unhealthy, which stops a Compose dependant from starting and fails
 * the deploy gate. {@link #reporting} is for a dependency whose loss degrades the server without
 * stopping it: the condition appears in the health message, where operators and monitoring can see
 * it, and the result stays healthy.
 *
 * <p>The distinction is not cosmetic. {@code cedarcli native health} and {@code docker status} exit
 * nonzero unless every check passes, and the runbooks gate deploys on that, so a gating check on a
 * dependency the server survives without blocks a deploy for a condition nobody needs to act on.
 * {@code CompToxHealthCheck} in the Bridge server argues that case at length, and reaches the
 * conclusion this class names {@code reporting}.
 *
 * <p>Each probe is bounded by its own timeout, because an unreachable dependency does not always
 * refuse a connection quickly. The Neo4j driver waits 30 seconds to establish one by default, and
 * every container health check gives the whole endpoint 10. An unbounded probe would therefore not
 * report a slow dependency; it would hang {@code /healthcheck} itself, and the container would read
 * as down for a reason no check names. At most one probe is ever in flight per check: a call that
 * finds the previous one still running waits for that one rather than starting a second, so a
 * permanently blocked dependency costs one thread rather than one per poll.
 */
public class CedarDependencyHealthCheck extends HealthCheck {

  /**
   * How long a probe may take before the check gives up on it.
   *
   * <p>Two seconds because the budget is shared. Dropwizard runs a registry's checks one after
   * another, the container health check gives the whole endpoint ten seconds, and the Worker
   * registers four dependency probes. Every probe here is a ping to a service on the same host or
   * Docker network — a Bolt handshake, a Redis PING, an OpenSearch HEAD — so two seconds is long
   * for a dependency that is answering at all, and four of them still fit the endpoint's budget.
   */
  static final long DEFAULT_TIMEOUT_MILLIS = 2_000;

  @FunctionalInterface
  public interface Probe {
    void verify() throws Exception;
  }

  private final String dependencyName;
  private final Probe probe;
  private final boolean gating;
  private final long timeoutMillis;
  private final ExecutorService prober;

  private Future<?> inFlight;

  private CedarDependencyHealthCheck(String dependencyName, Probe probe, boolean gating, long timeoutMillis) {
    this.dependencyName = dependencyName;
    this.probe = probe;
    this.gating = gating;
    this.timeoutMillis = timeoutMillis;
    this.prober = Executors.newSingleThreadExecutor(runnable -> {
      Thread thread = new Thread(runnable, "health-probe-" + dependencyName);
      thread.setDaemon(true);
      return thread;
    });
  }

  /** A dependency this server cannot serve requests without. Its failure makes the server unhealthy. */
  public static CedarDependencyHealthCheck gating(String dependencyName, Probe probe) {
    return new CedarDependencyHealthCheck(dependencyName, probe, true, DEFAULT_TIMEOUT_MILLIS);
  }

  /** A dependency whose loss degrades this server without stopping it. Its failure is reported, not failed on. */
  public static CedarDependencyHealthCheck reporting(String dependencyName, Probe probe) {
    return new CedarDependencyHealthCheck(dependencyName, probe, false, DEFAULT_TIMEOUT_MILLIS);
  }

  static CedarDependencyHealthCheck gating(String dependencyName, Probe probe, long timeoutMillis) {
    return new CedarDependencyHealthCheck(dependencyName, probe, true, timeoutMillis);
  }

  @Override
  protected Result check() {
    try {
      awaitProbe();
      return Result.healthy(dependencyName + " is reachable");
    } catch (TimeoutException e) {
      return outcome(dependencyName + " did not answer within " + timeoutMillis + "ms");
    } catch (Exception e) {
      return outcome(dependencyName + " is unreachable: " + rootMessage(e));
    }
  }

  /**
   * Runs the probe, or joins the one already running, and returns within the timeout either way.
   * An abandoned probe is left to finish on its own thread; the next call finds it and waits for
   * that one instead of queueing another behind it.
   */
  private synchronized void awaitProbe() throws Exception {
    if (inFlight == null || inFlight.isDone()) {
      inFlight = prober.submit(() -> {
        probe.verify();
        return null;
      });
    }
    Future<?> current = inFlight;
    try {
      current.get(timeoutMillis, TimeUnit.MILLISECONDS);
    } catch (java.util.concurrent.ExecutionException e) {
      Throwable cause = e.getCause();
      throw cause instanceof Exception ? (Exception) cause : new IllegalStateException(cause);
    }
  }

  /** Unhealthy for a gating dependency; healthy with the condition named for a reporting one. */
  private Result outcome(String condition) {
    return gating ? Result.unhealthy(condition)
        : Result.healthy(condition + ", and this server keeps serving without it");
  }

  private static String rootMessage(Throwable t) {
    Throwable root = t;
    while (root.getCause() != null && root.getCause() != root) {
      root = root.getCause();
    }
    return root.getClass().getSimpleName() + ": " + root.getMessage();
  }
}
