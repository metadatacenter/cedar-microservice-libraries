package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Hourly-UTC rollup of {@code log_request}, keyed by endpoint + status class + auth source.
 * <p>
 * One row per (hour, component, handler class+method, http method, status class, auth source). Any
 * timezone's day/week/weekend report is a SUM of the covered hour rows, folded at query time. Written
 * by additive upsert against the unique key; never by entity persist.
 */
@Entity
@Table(name = "agg_request_hourly",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_agg_request_hourly",
        columnNames = {"hourUtc", "systemComponentName", "className", "methodName", "httpMethod", "statusClass", "authSource"}),
    indexes = {
        @Index(columnList = "hourUtc", name = "IDX_agg_request_hourly_hourUtc"),
        @Index(columnList = "systemComponentName", name = "IDX_agg_request_hourly_component"),
        @Index(columnList = "statusClass", name = "IDX_agg_request_hourly_statusClass"),
        @Index(columnList = "authSource", name = "IDX_agg_request_hourly_authSource"),
    })
public class AggRequestHourly {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  /** Start of the UTC hour this row aggregates. */
  private Instant hourUtc;

  @Column(length = 16)
  private String systemComponentName;

  @Column(length = 85)
  private String className;

  @Column(length = 60)
  private String methodName;

  @Column(length = 6)
  private String httpMethod;

  /** "2xx" | "3xx" | "4xx" | "5xx" | "err" | "unknown" (history has no status -> "unknown"/"err"). */
  @Column(length = 8)
  private String statusClass;

  @Column(length = 9)
  private String authSource;

  private long reqCount;
  private long errorCount;
  private long sumHandlerNanos;
  private long minHandlerNanos;
  private long maxHandlerNanos;
  private long sumPreHandlerNanos;

  @Embedded
  private Histogram15 histogram = new Histogram15();

  /** One representative raw path for this endpoint bucket, for display. */
  @Column(length = 350)
  private String samplePath;

  public Long getId() {
    return id;
  }

  public Instant getHourUtc() {
    return hourUtc;
  }

  public String getSystemComponentName() {
    return systemComponentName;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public String getStatusClass() {
    return statusClass;
  }

  public String getAuthSource() {
    return authSource;
  }

  public long getReqCount() {
    return reqCount;
  }

  public long getErrorCount() {
    return errorCount;
  }

  public long getSumHandlerNanos() {
    return sumHandlerNanos;
  }

  public long getMinHandlerNanos() {
    return minHandlerNanos;
  }

  public long getMaxHandlerNanos() {
    return maxHandlerNanos;
  }

  public long getSumPreHandlerNanos() {
    return sumPreHandlerNanos;
  }

  public Histogram15 getHistogram() {
    return histogram;
  }

  public String getSamplePath() {
    return samplePath;
  }
}
