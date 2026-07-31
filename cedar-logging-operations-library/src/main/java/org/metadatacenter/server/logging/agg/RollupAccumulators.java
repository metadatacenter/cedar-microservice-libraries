package org.metadatacenter.server.logging.agg;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Map;

/**
 * In-memory fold target for one aggregation batch. Raw rows are folded in via {@code foldRequest} /
 * {@code foldCypher}; the DAO then flushes each accumulated bucket with a single additive upsert.
 * Accumulators are per-batch (short-lived) — cross-batch merging happens in the DB via the upsert, so
 * a batch that is re-run after a crash simply re-adds its own (rolled-back) delta.
 * <p>
 * Every dimension that is part of a rollup's UNIQUE key is coalesced to a non-null sentinel here,
 * because MySQL treats NULL as distinct in a unique key and the {@code ON DUPLICATE KEY UPDATE} merge
 * would silently create duplicate rows instead of adding.
 */
public class RollupAccumulators {

  public static final String NONE = "";        // sentinel for "no api key"
  public static final String UNKNOWN = "unknown";

  public record ReqKey(Instant hourUtc, String component, String className, String methodName,
                       String httpMethod, String statusClass, String authSource) {
  }

  public record UserKey(Instant hourUtc, String userId, String authSource, String apiKeyHash) {
  }

  public record CypherKey(Instant hourUtc, String component, String operation, String runnableHash) {
  }

  public static final class ReqAgg {
    public long reqCount, errorCount, sumHandlerNanos, sumPreHandlerNanos;
    public long minHandlerNanos = Long.MAX_VALUE, maxHandlerNanos = Long.MIN_VALUE;
    public final int[] hist = new int[LatencyHistogram.BUCKETS];
    public String samplePath;
  }

  public static final class UserAgg {
    public long reqCount, errorCount, sumHandlerNanos;
  }

  public static final class CypherAgg {
    public long execCount, sumNanos;
    public long minNanos = Long.MAX_VALUE, maxNanos = Long.MIN_VALUE;
    public final int[] hist = new int[LatencyHistogram.BUCKETS];
  }

  public static final class CatalogEntry {
    public String operation, runnableSample, interpolatedSample, className, methodName;
    public Instant firstSeen, lastSeen;
  }

  public final Map<ReqKey, ReqAgg> requests = new HashMap<>();
  public final Map<UserKey, UserAgg> users = new HashMap<>();
  public final Map<CypherKey, CypherAgg> cyphers = new HashMap<>();
  public final Map<String, CatalogEntry> catalog = new HashMap<>();

  public boolean isEmpty() {
    return requests.isEmpty() && users.isEmpty() && cyphers.isEmpty();
  }

  private static Instant hour(Instant t) {
    return t.truncatedTo(ChronoUnit.HOURS);
  }

  private static String orUnknown(String s) {
    return (s == null || s.isEmpty()) ? UNKNOWN : s;
  }

  /**
   * Fold one request row.
   *
   * @param userId       may be null (anonymous) — such rows are counted in the endpoint rollup but
   *                     skipped in the per-user rollup (there is no user to attribute them to)
   * @param apiKeyHash   md5 of the api key, or null for token/anonymous/history
   * @param handlerNanos handler duration in nanos (0 if unknown → folded but not histogrammed)
   */
  public void foldRequest(Instant when, String component, String className, String methodName,
                          String httpMethod, String statusClass, String authSource, String userId,
                          String apiKeyHash, long handlerNanos, long preHandlerNanos, boolean isError,
                          String path) {
    Instant h = hour(when);
    String comp = orUnknown(component);
    String cls = orUnknown(className);
    String mth = orUnknown(methodName);
    String verb = orUnknown(httpMethod);
    String sc = orUnknown(statusClass);
    String auth = orUnknown(authSource);

    ReqKey rk = new ReqKey(h, comp, cls, mth, verb, sc, auth);
    ReqAgg ra = requests.computeIfAbsent(rk, k -> new ReqAgg());
    ra.reqCount++;
    if (isError) {
      ra.errorCount++;
    }
    if (handlerNanos > 0) {
      ra.sumHandlerNanos += handlerNanos;
      ra.minHandlerNanos = Math.min(ra.minHandlerNanos, handlerNanos);
      ra.maxHandlerNanos = Math.max(ra.maxHandlerNanos, handlerNanos);
      ra.hist[LatencyHistogram.bucketForNanos(handlerNanos)]++;
    }
    if (preHandlerNanos > 0) {
      ra.sumPreHandlerNanos += preHandlerNanos;
    }
    if (ra.samplePath == null && path != null) {
      ra.samplePath = path.length() > 350 ? path.substring(0, 350) : path;
    }

    if (userId != null && !userId.isEmpty()) {
      UserKey uk = new UserKey(h, userId, auth, apiKeyHash == null ? NONE : apiKeyHash);
      UserAgg ua = users.computeIfAbsent(uk, k -> new UserAgg());
      ua.reqCount++;
      if (isError) {
        ua.errorCount++;
      }
      ua.sumHandlerNanos += Math.max(0, handlerNanos);
    }
  }

  /** Fold one Cypher row (also updates the query-text catalog for its hash). */
  public void foldCypher(Instant when, String component, String operation, String runnableHash,
                         long nanos, String runnableText, String interpolatedText, String className,
                         String methodName) {
    Instant h = hour(when);
    String comp = orUnknown(component);
    String op = orUnknown(operation);
    String hash = orUnknown(runnableHash);

    CypherKey ck = new CypherKey(h, comp, op, hash);
    CypherAgg ca = cyphers.computeIfAbsent(ck, k -> new CypherAgg());
    ca.execCount++;
    if (nanos > 0) {
      ca.sumNanos += nanos;
      ca.minNanos = Math.min(ca.minNanos, nanos);
      ca.maxNanos = Math.max(ca.maxNanos, nanos);
      ca.hist[LatencyHistogram.bucketForNanos(nanos)]++;
    }

    if (!NONE.equals(hash) && !UNKNOWN.equals(hash)) {
      CatalogEntry ce = catalog.computeIfAbsent(hash, k -> new CatalogEntry());
      if (ce.firstSeen == null || when.isBefore(ce.firstSeen)) {
        ce.firstSeen = when;
      }
      if (ce.lastSeen == null || when.isAfter(ce.lastSeen)) {
        ce.lastSeen = when;
      }
      if (ce.runnableSample == null) {
        ce.operation = op;
        ce.runnableSample = runnableText;
        ce.interpolatedSample = interpolatedText;
        ce.className = className;
        ce.methodName = methodName;
      }
    }
  }
}
