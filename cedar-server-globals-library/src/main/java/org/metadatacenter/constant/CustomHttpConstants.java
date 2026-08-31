package org.metadatacenter.constant;

public final class CustomHttpConstants {

  private CustomHttpConstants() {
  }

  // Custom HTTP headers
  public static final String HEADER_TOTAL_COUNT = "Total-Count";

  public static final String HEADER_CEDAR_VALIDATION_STATUS = "CEDAR-Validation-Status";
  public static final String HEADER_CEDAR_VALIDATION_REPORT = "CEDAR-Validation-Report";

  /**
   * The response headers a browser may read across origins.
   *
   * <p>A header CEDAR sends is invisible to cross-origin JavaScript unless it is named here, so the
   * artifact server's paging headers were being sent and could not be read. Two copies of this list
   * used to be maintained separately, in the response builder and in the shared bootstrap, and they had
   * already diverged in order; both now read this one.
   *
   * <p>Written as literals rather than as references to the JAX-RS constants because this library sits
   * below the ones that carry them.
   */
  public static final java.util.List<String> EXPOSED_HEADERS = java.util.List.of(
      "ETag",
      HEADER_CEDAR_VALIDATION_STATUS,
      "Content-Disposition",
      "Link",
      HEADER_TOTAL_COUNT);

  /** {@link #EXPOSED_HEADERS} as the comma-separated value the header itself takes. */
  public static final String EXPOSED_HEADERS_VALUE = String.join(",", EXPOSED_HEADERS);
}
