package org.metadatacenter.server.logging;

import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Decides which Cypher queries are worth a log row.
 * <p>
 * Every Cypher query in CEDAR produces an {@code AppLogType#CYPHER_QUERY} message, and one of them
 * is the user lookup that authenticates the request carrying it. That lookup therefore runs once per
 * request and is logged once per request: {@code log_cypher} holds 5.2M rows against
 * {@code log_request}'s 2.9M, and the authentication lookup is the difference. It is also the least
 * interesting row in the table - the same query, the same shape, one per request, with nothing to
 * learn from the repetition.
 * <p>
 * {@code AppLoggerQueueService.enqueueEvent} used to carry a commented-out filter dropping
 * <em>every</em> Cypher message, with the original author's note that this logging "is not required
 * on an ongoing basis". Dropping all of it is too blunt now: {@code log_cypher} is the input to the
 * hourly rollups, the query catalog and the outlier tables, which are what answer "this exact query
 * took 45s at 03:00". So this filter excludes named methods rather than the whole type - by default
 * the two authentication lookups, keeping every other query's analytics intact.
 * <p>
 * Configured through {@code CEDAR_LOG_CYPHER_EXCLUDED_METHODS}, read from the environment rather
 * than from {@code CedarConfig} because this class is constructed on the request path of all
 * fifteen services and an unset variable must never be able to stop one from booting. Accepts a
 * comma-separated list of {@code SimpleClassName.methodName} (or a bare {@code methodName}, which
 * matches in any class), plus two words: {@code *} excludes every Cypher message, restoring the
 * original blunt filter, and {@code none} excludes nothing.
 * <p>
 * Suppression is counted and reported periodically. A filter that removes a quarter of the traffic
 * while saying nothing is the same observability hole this one was written to help close.
 */
public final class CypherLogFilter {

  private static final Logger log = LoggerFactory.getLogger(CypherLogFilter.class);

  static final String ENV_VARIABLE = "CEDAR_LOG_CYPHER_EXCLUDED_METHODS";

  /**
   * The two lookups that resolve the caller's user record: by API key for {@code apiKey} auth, by id
   * for a Keycloak token. Both are {@code Neo4JProxyUser}, both run once per authenticated request,
   * and neither describes anything the request log does not already record.
   */
  static final String DEFAULT_EXCLUSIONS = "Neo4JProxyUser.findUserByApiKey,Neo4JProxyUser.findUserById";

  static final String EXCLUDE_ALL = "*";
  static final String EXCLUDE_NONE = "none";

  private static final long SUMMARY_EVERY = 10_000;

  private final boolean excludeEveryCypherMessage;
  private final Set<String> excludedSignatures;
  private final AtomicLong suppressed = new AtomicLong();

  public CypherLogFilter() {
    this(System.getenv(ENV_VARIABLE));
  }

  CypherLogFilter(String configuredValue) {
    String value = configuredValue == null ? DEFAULT_EXCLUSIONS : configuredValue.trim();
    if (value.isEmpty() || EXCLUDE_NONE.equalsIgnoreCase(value)) {
      excludeEveryCypherMessage = false;
      excludedSignatures = Set.of();
    } else if (EXCLUDE_ALL.equals(value)) {
      excludeEveryCypherMessage = true;
      excludedSignatures = Set.of();
    } else {
      excludeEveryCypherMessage = false;
      excludedSignatures = parse(value);
    }
    describe();
  }

  private static Set<String> parse(String value) {
    Set<String> signatures = new LinkedHashSet<>();
    Arrays.stream(value.split(","))
        .map(String::trim)
        .filter(entry -> !entry.isEmpty())
        .map(entry -> entry.toLowerCase(Locale.ROOT))
        .forEach(signatures::add);
    return Set.copyOf(signatures);
  }

  private void describe() {
    if (excludeEveryCypherMessage) {
      log.info("Cypher query logging is disabled entirely ({}={})", ENV_VARIABLE, EXCLUDE_ALL);
    } else if (excludedSignatures.isEmpty()) {
      log.info("Every Cypher query will be logged ({} excludes nothing)", ENV_VARIABLE);
    } else {
      log.info("Cypher query logging excludes {} ({})", excludedSignatures, ENV_VARIABLE);
    }
  }

  /**
   * @return whether this message should reach the queue. Only {@code CYPHER_QUERY} messages are ever
   * refused; every other type passes untouched.
   */
  public boolean accepts(AppLogMessage message) {
    if (message == null || message.getType() != AppLogType.CYPHER_QUERY) {
      return true;
    }
    if (excludeEveryCypherMessage) {
      countSuppressed();
      return false;
    }
    if (excludedSignatures.isEmpty()) {
      return true;
    }
    String methodName = message.getParamAsString(AppLogParam.METHOD_NAME);
    if (methodName == null) {
      // A message whose origin was not resolved is kept. The named exclusions are a volume
      // measure, and an unattributable query is the kind worth having rather than discarding.
      return true;
    }
    String method = methodName.toLowerCase(Locale.ROOT);
    if (excludedSignatures.contains(method)) {
      countSuppressed();
      return false;
    }
    String simpleClassName = simpleNameOf(message.getParamAsString(AppLogParam.CLASS_NAME));
    if (simpleClassName != null && excludedSignatures.contains(simpleClassName + "." + method)) {
      countSuppressed();
      return false;
    }
    return true;
  }

  private static String simpleNameOf(String className) {
    if (className == null) {
      return null;
    }
    String name = className.substring(className.lastIndexOf('.') + 1);
    return name.isEmpty() ? null : name.toLowerCase(Locale.ROOT);
  }

  private void countSuppressed() {
    long count = suppressed.incrementAndGet();
    if (count % SUMMARY_EVERY == 0) {
      log.info("Suppressed {} Cypher log messages so far ({})", count, ENV_VARIABLE);
    }
  }

  /** How many Cypher log messages this filter has refused. Never lost to a failure - deliberately dropped. */
  public long getSuppressedEventCount() {
    return suppressed.get();
  }
}
