package org.metadatacenter.server.neo4j;

import org.neo4j.driver.TransactionConfig;

import java.time.Duration;
import java.util.Set;

/**
 * The time limits graph transactions carry, so that no thread waits on the graph without a deadline.
 *
 * <p>Neo4j ends a transaction that outlives its limit and the driver reports it as a client error,
 * which is not retried. Without a limit a query runs for as long as it takes: production's query log
 * holds reads behind {@code GET /search} that ran for over an hour after nginx had answered the
 * caller at 180 seconds.</p>
 *
 * <p>{@link #REQUEST} applies to every query the proxies run. It equals the restore relay's
 * deadline, after which a save whose graph update has not committed is put back, so a transaction
 * still running at that point produces nothing anyone receives. From February to November 2026 no
 * production write took longer than 1.2 seconds, and no read longer than 26 seconds apart from two
 * searches.</p>
 *
 * <p>{@link #LONG_RUNNING} applies only to the queries that legitimately take longer, and none of
 * them runs on a request thread. The permission recalculation's read of every resource a group can
 * see took up to 49 seconds in production.</p>
 */
public final class GraphTransactionTimeouts {

  /** The limit on a graph transaction a request waits for. */
  public static final Duration REQUEST = Duration.ofSeconds(30);

  /** The limit on a graph transaction that a background task runs and that may take longer. */
  public static final Duration LONG_RUNNING = Duration.ofMinutes(10);

  /**
   * The limit on a graph transaction that holds a lock while it calls another service. It exceeds the
   * longest response a call to another CEDAR service waits for, 20 seconds by default, with room for
   * connecting and for the transaction's own work. A shorter limit would end the transaction while the
   * call is still out, and the call's outcome would go unrecorded.
   */
  public static final Duration AROUND_A_CALL = Duration.ofSeconds(60);

  private static final TransactionConfig REQUEST_CONFIG = TransactionConfig.builder().withTimeout(REQUEST).build();

  private static final TransactionConfig LONG_RUNNING_CONFIG =
      TransactionConfig.builder().withTimeout(LONG_RUNNING).build();

  private static final TransactionConfig AROUND_A_CALL_CONFIG =
      TransactionConfig.builder().withTimeout(AROUND_A_CALL).build();

  /**
   * The Neo4j error codes of a transaction that outlived its limit. A Neo4j 5.26 server reports the
   * second; the in-process server the tests run reports the first.
   */
  public static final Set<String> TIMED_OUT_CODES = Set.of("Neo.ClientError.Transaction.TransactionTimedOut",
      "Neo.ClientError.Transaction.TransactionTimedOutClientConfiguration");

  private GraphTransactionTimeouts() {
  }

  /** The transaction settings for a query a request waits for. */
  public static TransactionConfig request() {
    return REQUEST_CONFIG;
  }

  /** The transaction settings for a background query that may run longer than a request's limit. */
  public static TransactionConfig longRunning() {
    return LONG_RUNNING_CONFIG;
  }

  /** The transaction settings for a transaction that holds a lock while it calls another service. */
  public static TransactionConfig aroundACall() {
    return AROUND_A_CALL_CONFIG;
  }

  /** Whether the given Neo4j error code reports a transaction ended by its time limit. */
  public static boolean isTimeout(String code) {
    return code != null && TIMED_OUT_CODES.contains(code);
  }
}
