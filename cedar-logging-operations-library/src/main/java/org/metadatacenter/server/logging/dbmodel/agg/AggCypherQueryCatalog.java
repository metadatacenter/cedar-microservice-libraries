package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One row per distinct Cypher shape ({@code runnableHash}). Holds a single representative copy of the
 * query text so the millions of duplicated LOBs in {@code log_cypher} collapse to one row per shape —
 * the main disk win of the whole aggregation.
 */
@Entity
@Table(name = "agg_cypher_query_catalog",
    indexes = {
        @Index(columnList = "operation", name = "IDX_agg_cypher_catalog_operation"),
        @Index(columnList = "lastSeen", name = "IDX_agg_cypher_catalog_lastSeen"),
    })
public class AggCypherQueryCatalog {

  /** The md5 runnable-query hash; natural primary key. */
  @Id
  @Column(length = 32)
  private String runnableHash;

  @Column(length = 13)
  private String operation;

  /**
   * Explicit length is REQUIRED, not decoration. Hibernate 6 sizes an {@code @Lob} String from the
   * column length, and the default 255 makes MySQL pick TINYTEXT — so every catalog insert for a
   * query longer than 255 chars fails with a DataException and takes the whole Cypher aggregation
   * batch down with it (observed 2026-07-31: 309 failures, 6 of 224,529 rows aggregated, real
   * queries up to 4,574 chars).
   * <p>
   * Note the same {@code @Lob} on {@code ApplicationCypherLog} produced LONGTEXT, because those
   * tables were created under Hibernate 5, which ignored length for LOBs. Do not copy that pattern
   * into a new entity without a length.
   */
  @Lob
  @Column(length = 65535)
  private String runnableSample;

  @Lob
  @Column(length = 65535)
  private String interpolatedSample;

  @Column(length = 85)
  private String sampleClassName;

  @Column(length = 60)
  private String sampleMethodName;

  private Instant firstSeen;
  private Instant lastSeen;

  public String getRunnableHash() {
    return runnableHash;
  }

  public String getOperation() {
    return operation;
  }

  public String getRunnableSample() {
    return runnableSample;
  }

  public String getInterpolatedSample() {
    return interpolatedSample;
  }

  public String getSampleClassName() {
    return sampleClassName;
  }

  public String getSampleMethodName() {
    return sampleMethodName;
  }

  public Instant getFirstSeen() {
    return firstSeen;
  }

  public Instant getLastSeen() {
    return lastSeen;
  }
}
