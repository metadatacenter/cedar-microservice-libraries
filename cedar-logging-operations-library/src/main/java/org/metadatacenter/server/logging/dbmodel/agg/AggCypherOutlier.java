package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Retained forever: the slowest Cypher query INSTANCES with full text + parameters, so a suspicious
 * long-running query is still inspectable after the raw {@code log_cypher} row is pruned. Filled by the
 * aggregator per settled day (top-N slow) and once over history at backfill.
 */
@Entity
@Table(name = "agg_cypher_outlier",
    indexes = {
        @Index(columnList = "logTime", name = "IDX_agg_cypher_outlier_logTime"),
        @Index(columnList = "durationNanos", name = "IDX_agg_cypher_outlier_durationNanos"),
        @Index(columnList = "runnableHash", name = "IDX_agg_cypher_outlier_runnableHash"),
    })
public class AggCypherOutlier {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant logTime;

  @Column(length = 16)
  private String systemComponentName;

  @Column(length = 13)
  private String operation;

  @Column(length = 32)
  private String runnableHash;

  private long durationNanos;

  @Lob
  private String runnable;

  @Lob
  private String interpolated;

  @Lob
  private String parameters;

  @Column(length = 85)
  private String className;

  @Column(length = 60)
  private String methodName;

  public Long getId() {
    return id;
  }

  public Instant getLogTime() {
    return logTime;
  }

  public String getSystemComponentName() {
    return systemComponentName;
  }

  public String getOperation() {
    return operation;
  }

  public String getRunnableHash() {
    return runnableHash;
  }

  public long getDurationNanos() {
    return durationNanos;
  }

  public String getRunnable() {
    return runnable;
  }

  public String getInterpolated() {
    return interpolated;
  }

  public String getParameters() {
    return parameters;
  }

  public String getClassName() {
    return className;
  }

  public String getMethodName() {
    return methodName;
  }
}
