package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import org.metadatacenter.server.logging.agg.LatencyHistogram;

/**
 * The 15 latency-histogram bucket columns (h0..h14), embedded flat into each rollup table so they
 * merge by column-wise SUM at query time. See {@link LatencyHistogram}.
 * <p>
 * Rollup rows are written by native additive-upsert SQL (not entity persist), so this type is a plain
 * column holder for the read side + a place to hang array helpers used when folding.
 */
@Embeddable
public class Histogram15 {

  @Column private int h0;
  @Column private int h1;
  @Column private int h2;
  @Column private int h3;
  @Column private int h4;
  @Column private int h5;
  @Column private int h6;
  @Column private int h7;
  @Column private int h8;
  @Column private int h9;
  @Column private int h10;
  @Column private int h11;
  @Column private int h12;
  @Column private int h13;
  @Column private int h14;

  public int[] toArray() {
    return new int[]{h0, h1, h2, h3, h4, h5, h6, h7, h8, h9, h10, h11, h12, h13, h14};
  }

  public void setFromArray(int[] a) {
    h0 = a[0]; h1 = a[1]; h2 = a[2]; h3 = a[3]; h4 = a[4];
    h5 = a[5]; h6 = a[6]; h7 = a[7]; h8 = a[8]; h9 = a[9];
    h10 = a[10]; h11 = a[11]; h12 = a[12]; h13 = a[13]; h14 = a[14];
  }
}
