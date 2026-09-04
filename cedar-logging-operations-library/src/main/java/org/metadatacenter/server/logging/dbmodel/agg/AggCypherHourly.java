package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Hourly-UTC rollup of {@code log_cypher}, keyed by query shape ({@code runnableHash}) + operation.
 * The full query text lives once per hash in {@link AggCypherQueryCatalog}, so this table stays tiny.
 */
@Entity
@Table(name = "agg_cypher_hourly",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_agg_cypher_hourly",
        columnNames = {"hourUtc", "systemComponentName", "operation", "runnableHash"}),
    indexes = {
        @Index(columnList = "hourUtc", name = "IDX_agg_cypher_hourly_hourUtc"),
        @Index(columnList = "runnableHash", name = "IDX_agg_cypher_hourly_runnableHash"),
        @Index(columnList = "operation", name = "IDX_agg_cypher_hourly_operation"),
    })
public class AggCypherHourly {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant hourUtc;

  @Column(length = 16)
  private String systemComponentName;

  @Column(length = 13)
  private String operation;

  @Column(length = 32)
  private String runnableHash;

  private long execCount;
  private long sumNanos;
  private long minNanos;
  private long maxNanos;

  @Embedded
  private Histogram15 histogram = new Histogram15();

  public Long getId() {
    return id;
  }

  public Instant getHourUtc() {
    return hourUtc;
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

  public long getExecCount() {
    return execCount;
  }

  public long getSumNanos() {
    return sumNanos;
  }

  public long getMinNanos() {
    return minNanos;
  }

  public long getMaxNanos() {
    return maxNanos;
  }

  public Histogram15 getHistogram() {
    return histogram;
  }
}
