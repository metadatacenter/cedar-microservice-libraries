package org.metadatacenter.server.logging.agg;

import io.dropwizard.hibernate.UnitOfWork;
import org.metadatacenter.server.logging.dao.agg.AggregationRollupDAO;
import org.metadatacenter.server.logging.dao.agg.LogAggregationStateDAO;
import org.metadatacenter.server.logging.dbmodel.agg.LogAggregationState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * The aggregation engine. Each {@code @UnitOfWork} method is one DB transaction, so a batch's rollup
 * upserts and its cursor advance commit together — the crash-safety contract from the plan (§5.0):
 * commit → cursor moved; die mid-batch → the whole batch rolls back and re-runs on restart, exactly
 * once, no double-count.
 * <p>
 * Wrapped with {@code UnitOfWorkAwareProxyFactory} (like {@code AppLoggerExecutorService}) so the
 * annotations take effect on the worker's background thread.
 * <p>
 * Phase 2a implements the historical (backfill) driver over {@code *_pre284}. The live driver
 * (settled UTC days over {@code log_request}/{@code log_cypher}) will reuse {@link #rollupDAO} and the
 * same fold, differing only in the source query and the state bucket key.
 */
public class LogAggregationService {

  private static final Logger log = LoggerFactory.getLogger(LogAggregationService.class);

  public static final String BACKFILL_BUCKET = "backfill";
  public static final String SRC_REQUEST_HIST = "log_request_pre284";
  public static final String SRC_CYPHER_HIST = "log_cypher_pre284";

  private final AggregationRollupDAO rollupDAO;
  private final LogAggregationStateDAO stateDAO;

  public LogAggregationService(AggregationRollupDAO rollupDAO, LogAggregationStateDAO stateDAO) {
    this.rollupDAO = rollupDAO;
    this.stateDAO = stateDAO;
  }

  /** Create/seed the backfill state row for a source table (records the target maxId for progress). */
  @UnitOfWork
  public void initBackfillState(String sourceTable) {
    LogAggregationState s = stateDAO.findOrCreate(sourceTable, BACKFILL_BUCKET);
    if (s.getStartedAt() == null) {
      s.setStartedAt(Instant.now());
    }
    if (s.getCursorId() == null) {
      s.setCursorId(0L);
      s.setMinId(0L);
    }
    s.setMaxId(rollupDAO.maxId(sourceTable));
    if (s.getStatus() == LogAggregationState.Status.PENDING) {
      s.setStatus(LogAggregationState.Status.RUNNING);
    }
    stateDAO.save(s);
  }

  /**
   * Process one batch of the request-log history. Reads from the cursor, folds, flushes, advances the
   * cursor — all in this single transaction.
   *
   * @return rows read this batch; 0 means the source is drained
   */
  @UnitOfWork
  public long runBackfillRequestBatch(int batchSize) {
    LogAggregationState s = stateDAO.find(SRC_REQUEST_HIST, BACKFILL_BUCKET);
    long fromId = s.getCursorId() == null ? 0L : s.getCursorId();
    RollupAccumulators acc = new RollupAccumulators();
    long[] res = rollupDAO.foldHistoricalRequestBatch(acc, fromId, batchSize);
    long rowsRead = res[0];
    if (rowsRead == 0) {
      return 0;
    }
    rollupDAO.flush(acc);
    s.setCursorId(res[1]);
    s.setRowsIn(s.getRowsIn() + rowsRead);
    s.setRowsOut(s.getRowsOut() + acc.requests.size() + acc.users.size());
    stateDAO.save(s);
    return rowsRead;
  }

  @UnitOfWork
  public long runBackfillCypherBatch(int batchSize) {
    LogAggregationState s = stateDAO.find(SRC_CYPHER_HIST, BACKFILL_BUCKET);
    long fromId = s.getCursorId() == null ? 0L : s.getCursorId();
    RollupAccumulators acc = new RollupAccumulators();
    long[] res = rollupDAO.foldHistoricalCypherBatch(acc, fromId, batchSize);
    long rowsRead = res[0];
    if (rowsRead == 0) {
      return 0;
    }
    rollupDAO.flush(acc);
    s.setCursorId(res[1]);
    s.setRowsIn(s.getRowsIn() + rowsRead);
    s.setRowsOut(s.getRowsOut() + acc.cyphers.size());
    stateDAO.save(s);
    return rowsRead;
  }

  /**
   * Verify row-count parity and mark the source ready to drop. We compare rows read against the live
   * source count; a small shortfall from rows with no timestamp (unbucketable, skipped) is logged, not
   * failed. The actual DROP stays a deliberate manual step — never automatic.
   */
  @UnitOfWork
  public void markBackfillComplete(String sourceTable) {
    LogAggregationState s = stateDAO.find(sourceTable, BACKFILL_BUCKET);
    long sourceCount = rollupDAO.count(sourceTable);
    long skipped = sourceCount - s.getRowsIn();
    s.setFinishedAt(Instant.now());
    if (skipped == 0) {
      s.setStatus(LogAggregationState.Status.READY_TO_DROP);
      log.info("Backfill of {} complete: {} rows aggregated, parity OK -> READY_TO_DROP (drop manually).",
          sourceTable, s.getRowsIn());
    } else {
      s.setStatus(LogAggregationState.Status.READY_TO_DROP);
      s.setErrorText("parity: source=" + sourceCount + " aggregated=" + s.getRowsIn()
          + " skipped(no-timestamp?)=" + skipped);
      log.warn("Backfill of {} complete with {} unaggregated rows (likely no timestamp). "
          + "Review before dropping. source={} aggregated={}", sourceTable, skipped, sourceCount, s.getRowsIn());
    }
    stateDAO.save(s);
  }

  // ---- live (ongoing) driver ---------------------------------------------------------------------

  /** Earliest not-yet-aggregated request time (drives the catch-up day loop); null if all caught up. */
  @UnitOfWork
  public Instant earliestUnaggregatedRequestTime() {
    return rollupDAO.earliestUnaggregated("requestTime", "log_request");
  }

  @UnitOfWork
  public Instant earliestUnaggregatedCypherTime() {
    return rollupDAO.earliestUnaggregated("logTime", "log_cypher");
  }

  /**
   * Aggregate one batch of a settled UTC day of {@code log_request}: fold, additive-upsert the hourly
   * rollups, and mark the batch's raw rows {@code aggregatedAt} — all in this one transaction, so a
   * crash rolls the whole batch back (rows stay unmarked, deltas undone) for clean reprocessing.
   *
   * @return rows read this batch; 0 means the day is fully aggregated
   */
  @UnitOfWork
  public long aggregateLiveRequestBatch(Instant dayStart, Instant dayEnd, int batchSize) {
    RollupAccumulators acc = new RollupAccumulators();
    long[] res = rollupDAO.foldLiveRequestBatch(acc, dayStart, dayEnd, batchSize);
    if (res[0] == 0) {
      return 0;
    }
    rollupDAO.flush(acc);
    rollupDAO.markLiveRequestRows(dayStart, dayEnd, res[1], Instant.now());
    return res[0];
  }

  @UnitOfWork
  public long aggregateLiveCypherBatch(Instant dayStart, Instant dayEnd, int batchSize) {
    RollupAccumulators acc = new RollupAccumulators();
    long[] res = rollupDAO.foldLiveCypherBatch(acc, dayStart, dayEnd, batchSize);
    if (res[0] == 0) {
      return 0;
    }
    rollupDAO.flush(acc);
    rollupDAO.markLiveCypherRows(dayStart, dayEnd, res[1], Instant.now());
    return res[0];
  }

  // ---- prune (deletes only rows that are aggregated AND past retention) ---------------------------

  @UnitOfWork
  public int pruneRequests(Instant cutoff, int batchSize) {
    return rollupDAO.pruneRequests(cutoff, batchSize);
  }

  @UnitOfWork
  public int pruneCypher(Instant cutoff, int batchSize) {
    return rollupDAO.pruneCypher(cutoff, batchSize);
  }
}
