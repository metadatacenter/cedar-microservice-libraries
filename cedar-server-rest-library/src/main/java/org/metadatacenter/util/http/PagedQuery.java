package org.metadatacenter.util.http;

import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.Optional;

public class PagedQuery {

  private Optional<Integer> limitInput;
  private Optional<Integer> offsetInput;

  private final PaginationConfig config;
  private int limit;
  private int offset;


  public PagedQuery(PaginationConfig config) {
    this.config = config;
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

  protected void validateLimit() throws CedarException {
    int limitDefault = config.getDefaultPageSize();
    int limitMax = config.getMaxPageSize();
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
    }
  }


}
