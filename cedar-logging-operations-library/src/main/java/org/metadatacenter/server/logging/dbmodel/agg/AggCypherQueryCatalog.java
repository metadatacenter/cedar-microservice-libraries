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

  @Lob
  private String runnableSample;

  @Lob
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
