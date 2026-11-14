package org.metadatacenter.util.http;

import jakarta.ws.rs.core.MultivaluedMap;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.List;

/**
 * Reads the modified-date window of a listing from its query parameters. Each bound is a time in epoch
 * milliseconds. A refusal identifies the parameter at fault and says what was wrong with it.
 */
public final class ModifiedDateQuery {
  public static final String AFTER = "modified_after";
  public static final String BEFORE = "modified_before";

  /**
   * The smallest bound accepted, 100,000,000,000 milliseconds, which falls in March 1973. CEDAR holds
   * nothing that old, so a bound below it can only exclude everything or nothing. A time in seconds
   * stays below it until the year 5138, so a client that sends seconds where milliseconds are expected
   * receives an error rather than a wrong listing.
   */
  public static final long SMALLEST_BOUND = 100_000_000_000L;

  private ModifiedDateQuery() {}

  public static ModifiedDateRange parse(MultivaluedMap<String, String> query) throws CedarException {
    Long after = bound(query, AFTER);
    Long before = bound(query, BEFORE);
    if (after != null && before != null && after >= before) {
      throw new CedarAssertionException(BEFORE + " must be later than " + AFTER + ", and " + before
          + " is not later than " + after + ".")
          .parameter(AFTER, after)
          .parameter(BEFORE, before);
    }
    return new ModifiedDateRange(after, before);
  }

  private static Long bound(MultivaluedMap<String, String> query, String name) throws CedarException {
    List<String> values = query.get(name);
    if (values == null) {
      return null;
    }
    if (values.size() != 1) {
      throw new CedarAssertionException(name + " may be given once, and it was given " + values.size() + " times.")
          .parameter(name, values);
    }
    String value = values.get(0);
    long milliseconds;
    try {
      milliseconds = Long.parseLong(value);
    } catch (NumberFormatException e) {
      throw new CedarAssertionException(name + " must be a time in epoch milliseconds, and '" + value
          + "' is not one.")
          .parameter(name, value);
    }
    if (milliseconds < SMALLEST_BOUND) {
      throw new CedarAssertionException(name + " must be a time in epoch milliseconds. Read that way, " + value
          + " falls before March 1973, earlier than anything CEDAR holds. If it is a time in seconds, multiply it "
          + "by 1000.")
          .parameter(name, value);
    }
    return milliseconds;
  }
}
