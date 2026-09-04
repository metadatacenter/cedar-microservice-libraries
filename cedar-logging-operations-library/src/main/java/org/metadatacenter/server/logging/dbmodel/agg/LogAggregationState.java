package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Orchestration / idempotency / observability for the aggregation jobs. One row per unit of work:
 * <ul>
 *   <li>live path: one row per source table per UTC day being aggregated ({@code bucket} = "2026-07-29");</li>
 *   <li>backfill path: one row per source {@code *_pre284} table, with {@code cursorId} advancing by
 *       id-range so a crashed/restarted drain resumes exactly where it left off.</li>
 * </ul>
 * A bucket reaches {@link Status#AGGREGATED}/{@link Status#COMPLETE} only after its rollups are
 * committed; that is what makes re-runs skip and crashes safe.
 */
@Entity
@Table(name = "log_aggregation_state",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_log_aggregation_state",
        columnNames = {"sourceTable", "bucket"}),
    indexes = {
        @Index(columnList = "sourceTable", name = "IDX_log_agg_state_sourceTable"),
        @Index(columnList = "status", name = "IDX_log_agg_state_status"),
    })
public class LogAggregationState {

  public enum Status {
    PENDING, RUNNING, AGGREGATED, COMPLETE, READY_TO_DROP, FAILED
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** "log_request" | "log_cypher" | "log_request_pre284" | "log_cypher_pre284". */
  @Column(length = 32)
  private String sourceTable;

  /** UTC day ("2026-07-29") for the live path, or a fixed label like "backfill" for the drain. */
  @Column(length = 40)
  private String bucket;

  @Enumerated(EnumType.STRING)
  @Column(length = 16)
  private Status status;

  /** Backfill cursor: the greatest source id already folded + committed. */
  private Long cursorId;
  private Long minId;
  private Long maxId;

  private long rowsIn;
  private long rowsOut;

  private Instant startedAt;
  private Instant finishedAt;

  @Column(length = 1000)
  private String errorText;

  public Long getId() {
    return id;
  }

  public String getSourceTable() {
    return sourceTable;
  }

  public void setSourceTable(String sourceTable) {
    this.sourceTable = sourceTable;
  }

  public String getBucket() {
    return bucket;
  }

  public void setBucket(String bucket) {
    this.bucket = bucket;
  }

  public Status getStatus() {
    return status;
  }

  public void setStatus(Status status) {
    this.status = status;
  }

  public Long getCursorId() {
    return cursorId;
  }

  public void setCursorId(Long cursorId) {
    this.cursorId = cursorId;
  }

  public Long getMinId() {
    return minId;
  }

  public void setMinId(Long minId) {
    this.minId = minId;
  }

  public Long getMaxId() {
    return maxId;
  }

  public void setMaxId(Long maxId) {
    this.maxId = maxId;
  }

  public long getRowsIn() {
    return rowsIn;
  }

  public void setRowsIn(long rowsIn) {
    this.rowsIn = rowsIn;
  }

  public long getRowsOut() {
    return rowsOut;
  }

  public void setRowsOut(long rowsOut) {
    this.rowsOut = rowsOut;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Instant startedAt) {
    this.startedAt = startedAt;
  }

  public Instant getFinishedAt() {
    return finishedAt;
  }

  public void setFinishedAt(Instant finishedAt) {
    this.finishedAt = finishedAt;
  }

  public String getErrorText() {
    return errorText;
  }

  public void setErrorText(String errorText) {
    this.errorText = errorText;
  }
}
