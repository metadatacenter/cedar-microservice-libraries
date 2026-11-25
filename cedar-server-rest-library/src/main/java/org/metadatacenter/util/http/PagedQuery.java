package org.metadatacenter.util.http;

import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.metadatacenter.constant.CedarQueryParameters.QP_PAGE;
import static org.metadatacenter.constant.CedarQueryParameters.QP_PAGE_SIZE;
import static org.metadatacenter.constant.CedarQueryParameters.QP_PAGE_SIZE_CAMEL;

public class PagedQuery {

  /** The query parameters of the withdrawn page-number paging. A request carrying one is refused. */
  public static final List<String> PAGE_NUMBER_PARAMETERS = List.of(QP_PAGE, QP_PAGE_SIZE_CAMEL, QP_PAGE_SIZE);

  private Map<String, List<String>> queryParameters;
  private Optional<Integer> limitInput;
  private Optional<Integer> offsetInput;

  private final PaginationConfig config;
  private final int defaultPageSize;
  private final int maxPageSize;
  private Integer maxOffset;
  private int limit;
  private int offset;


  public PagedQuery(PaginationConfig config) {
    this.config = config;
    this.defaultPageSize = config.getDefaultPageSize();
    this.maxPageSize = config.getMaxPageSize();
    this.limitInput = Optional.empty();
    this.offsetInput = Optional.empty();
  }

  /**
   * A query whose page sizes are fixed by the route rather than read from the shared configuration.
   * {@link #getPaginationConfig()} answers null for such a query.
   */
  public PagedQuery(int defaultPageSize, int maxPageSize) {
    this.config = null;
    this.defaultPageSize = defaultPageSize;
    this.maxPageSize = maxPageSize;
    this.limitInput = Optional.empty();
    this.offsetInput = Optional.empty();
  }

  public PagedQuery limit(Optional<Integer> limitInput) {
    this.limitInput = limitInput;
    return this;
  }

  public PagedQuery offset(Optional<Integer> offsetInput) {
    this.offsetInput = offsetInput;
    return this;
  }

  /**
   * The query parameters of the request, so that {@link #validate()} refuses one carrying a withdrawn
   * page-number parameter. Without them, an undeclared {@code page} would be ignored and the request
   * answered from offset 0, as though it had asked for the first page.
   */
  public PagedQuery queryParameters(Map<String, List<String>> queryParameters) {
    this.queryParameters = queryParameters;
    return this;
  }

  /** Refuses an offset beyond this one, for a listing whose deep pages are too costly to serve. */
  public PagedQuery maxOffset(int maxOffset) {
    this.maxOffset = maxOffset;
    return this;
  }

  public void validate() throws CedarException {
    validatePageNumbers();
    validateLimit();
    validateOffset();
  }

  public int getLimit() {
    return limit;
  }

  public int getOffset() {
    return offset;
  }

  protected PaginationConfig getPaginationConfig() {
    return config;
  }

  protected void validatePageNumbers() throws CedarException {
    if (queryParameters != null) {
      refusePageNumbers(queryParameters, pageNumbersWithdrawn(defaultPageSize, maxPageSize));
    }
  }

  protected void validateLimit() throws CedarException {
    int limitDefault = defaultPageSize;
    int limitMax = maxPageSize;
    limit = limitDefault;
    if (limitInput.isPresent()) {
      limit = limitInput.get();
      // A bad limit is a client mistake, so it must be a 400. Without badRequest() the error pack
      // keeps its default INTERNAL_SERVER_ERROR status and a caller's typo answers 500.
      if (limit <= 0) {
        throw new CedarAssertionException("You should specify a positive limit!")
            .parameter("limit", limit).badRequest();
      } else if (limit > limitMax) {
        throw new CedarAssertionException("You should specify a limit smaller than " + limitMax + "!")
            .parameter("limit", limit).badRequest();
      }
    }
  }

  protected void validateOffset() throws CedarException {
    offset = 0;
    if (offsetInput.isPresent()) {
      if (offsetInput.get() < 0) {
        throw new CedarAssertionException("You should specify a positive or zero offset!")
            .parameter("offset", offsetInput.get()).badRequest();
      }
      offset = offsetInput.get();
      if (maxOffset != null && offset > maxOffset) {
        throw new CedarAssertionException("You should specify an offset no larger than " + maxOffset + "!")
            .parameter("offset", offset).badRequest();
      }
    }
  }

  /** The message a page-number request is refused with, naming the paging that replaced it. */
  public static String pageNumbersWithdrawn(int defaultPageSize, int maxPageSize) {
    return "Paging by page number is no longer supported. Send limit (1 to " + maxPageSize + ", default "
        + defaultPageSize + ") and offset (default 0) instead.";
  }

  /** The first withdrawn page-number parameter a request carries, if it carries one. */
  public static Optional<String> pageNumberParameter(Map<String, List<String>> queryParameters) {
    return PAGE_NUMBER_PARAMETERS.stream().filter(queryParameters::containsKey).findFirst();
  }

  /**
   * Refuses a request carrying a withdrawn page-number parameter with a 400 that names the parameter
   * and the value sent.
   */
  public static void refusePageNumbers(Map<String, List<String>> queryParameters, String message)
      throws CedarException {
    Optional<String> name = pageNumberParameter(queryParameters);
    if (name.isPresent()) {
      List<String> values = queryParameters.get(name.get());
      String value = values == null || values.isEmpty() ? null : values.get(0);
      throw new CedarAssertionException(message).parameter(name.get(), value).badRequest();
    }
  }

}
