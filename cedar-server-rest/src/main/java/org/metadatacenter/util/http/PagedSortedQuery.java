package org.metadatacenter.util.http;

import org.apache.commons.lang.StringUtils;
import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PagedSortedQuery extends PagedQuery {

  private Optional<String> sortInput;

  private List<String> sortList;

  public PagedSortedQuery(PaginationConfig config) {
    super(config);
    sortList = new ArrayList<>();
  }

  public PagedSortedQuery sort(Optional<String> sortInput) {
    this.sortInput = sortInput;
    return this;
  }

  @Override
  public PagedSortedQuery limit(Optional<Integer> limitInput) {
    super.limit(limitInput);
    return this;
  }

  @Override
  public PagedSortedQuery offset(Optional<Integer> offsetInput) {
    super.offset(offsetInput);
    return this;
  }

  @Override
  public void validate() throws CedarException {
    validateLimit();
    validateOffset();
    validateSorting();
  }

  public List<String> getSortList() {
    return sortList;
  }

  public String getSortListAsString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < sortList.size(); i++) {
      sb.append(sortList.get(i));
      if (i != sortList.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

  protected void validateSorting() throws CedarException {
    //String sortString = sortInput.orElseGet(() -> QuerySortOptions.getDefaultSortField().getName());
    if (sortInput.isEmpty()) {
      sortList = new ArrayList<>();
    }
    else {
      sortList = Arrays.asList(StringUtils.split(sortInput.get().trim(), ","));
      for (String s : sortList) {
        String test = s;
        if (s != null && s.startsWith("-")) {
          test = s.substring(1);
        }
        if (!QuerySortOptions.isKnownField(test)) {
          throw new CedarAssertionException("You passed an illegal sort type:'" + s + "'. The allowed values are:" +
              QuerySortOptions.getKnownFieldNames())
              .parameter("sort", s)
              .parameter("allowedSort", QuerySortOptions.getKnownFieldNames());
        }
      }
    }
  }

}
