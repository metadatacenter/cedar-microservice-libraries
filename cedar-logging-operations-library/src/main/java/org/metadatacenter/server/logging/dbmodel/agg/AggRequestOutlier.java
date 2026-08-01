package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Retained forever: the slowest and error request INSTANCES (not just distributions), so "this exact
 * call at 03:00 by user X took 45s" is still answerable after the raw {@code log_request} row is pruned.
 * Filled by the aggregator per settled day (top-N slow + top-M error) and once over history at backfill.
 */
@Entity
@Table(name = "agg_request_outlier",
    indexes = {
        @Index(columnList = "requestTime", name = "IDX_agg_request_outlier_requestTime"),
        @Index(columnList = "durationNanos", name = "IDX_agg_request_outlier_durationNanos"),
        @Index(columnList = "kind", name = "IDX_agg_request_outlier_kind"),
        @Index(columnList = "userId", name = "IDX_agg_request_outlier_userId"),
    })
public class AggRequestOutlier {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant requestTime;

  @Column(length = 16)
  private String systemComponentName;

  @Column(length = 6)
  private String httpMethod;

  @Column(length = 350)
  private String path;

  @Column(length = 85)
  private String className;

  @Column(length = 60)
  private String methodName;

  @Column(length = 70)
  private String userId;

  @Column(length = 9)
  private String authSource;

  @Column(length = 32)
  private String apiKeyHash;

  private Integer status;

  private long durationNanos;

  /** "SLOW" or "ERROR". */
  @Column(length = 8)
  private String kind;

  @Lob
  private String errorPack;

  public Long getId() {
    return id;
  }

  public Instant getRequestTime() {
    return requestTime;
  }

  public String getSystemComponentName() {
    return systemComponentName;
  }

  public String getHttpMethod() {
    return httpMethod;
  }

  public String getPath() {
    return path;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getUserId() {
    return userId;
  }

  public String getAuthSource() {
    return authSource;
  }

  public String getApiKeyHash() {
    return apiKeyHash;
  }

  public Integer getStatus() {
    return status;
  }

  public long getDurationNanos() {
    return durationNanos;
  }

  public String getKind() {
    return kind;
  }

  public String getErrorPack() {
    return errorPack;
  }
}
