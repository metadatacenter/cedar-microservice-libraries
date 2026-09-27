package org.metadatacenter.util.http;

import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.Optional;

public class PagedQuery {

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

  /** Refuses an offset beyond this one, for a listing whose deep pages are too costly to serve. */
  public PagedQuery maxOffset(int maxOffset) {
    this.maxOffset = maxOffset;
    return this;
  }

  public void validate() throws CedarException {
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


}
