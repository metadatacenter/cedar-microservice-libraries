package org.metadatacenter.util.http;

import jakarta.ws.rs.core.MultivaluedMap;
import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.rest.exception.CedarAssertionException;

public final class ModifiedDateQuery {
  public static final String AFTER = "modified_after";
  public static final String BEFORE = "modified_before";
  private ModifiedDateQuery() {}

  public static ModifiedDateRange parse(MultivaluedMap<String, String> query) throws org.metadatacenter.exception.CedarException {
    try {
      return new ModifiedDateRange(bound(query, AFTER), bound(query, BEFORE));
    } catch (IllegalArgumentException e) {
      throw new CedarAssertionException("Modified-date bounds must be epoch milliseconds, with modified_before later than modified_after.").badRequest();
    }
  }

  private static Long bound(MultivaluedMap<String, String> query, String name) {
    if (!query.containsKey(name)) return null;
    if (query.get(name).size() != 1) throw new IllegalArgumentException("Repeated bound");
    return Long.valueOf(query.getFirst(name));
  }
}
