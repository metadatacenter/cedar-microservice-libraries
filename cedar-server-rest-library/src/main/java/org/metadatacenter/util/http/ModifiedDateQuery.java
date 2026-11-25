package org.metadatacenter.util.http;

import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

import static org.metadatacenter.constant.CedarQueryParameters.QP_MODIFIED_AFTER;
import static org.metadatacenter.constant.CedarQueryParameters.QP_MODIFIED_BEFORE;

/**
 * Reads the modified-date window of a listing from its query parameters. Each bound is an ISO-8601 instant
 * with a UTC designator or a numeric offset, such as 2026-11-25T00:00:00Z or 2026-11-25T01:00:00+01:00.
 * {@code modified_after} is inclusive and {@code modified_before} exclusive. A refusal identifies the
 * parameter at fault and says what was wrong with it.
 */
public final class ModifiedDateQuery {

  /** The description each route gives {@code modified_after}. */
  public static final String AFTER_DESCRIPTION = "Inclusive last-modified lower bound, as an ISO-8601 instant "
      + "such as 2026-11-25T00:00:00Z. An offset such as +01:00 is accepted in place of Z, percent-encoded as "
      + "%2B01:00. A number is refused.";
  /** The description each route gives {@code modified_before}. */
  public static final String BEFORE_DESCRIPTION = "Exclusive last-modified upper bound, as an ISO-8601 instant "
      + "such as 2026-11-25T00:00:00Z. An offset such as +01:00 is accepted in place of Z, percent-encoded as "
      + "%2B01:00. A number is refused.";

  private ModifiedDateQuery() {}

  /**
   * Reads the window from every value given for each bound, so that a bound given twice is refused rather
   * than read as its first value. A null or empty list means the bound is absent.
   */
  public static ModifiedDateRange parse(List<String> afterValues, List<String> beforeValues) throws CedarException {
    Long after = bound(QP_MODIFIED_AFTER, afterValues);
    Long before = bound(QP_MODIFIED_BEFORE, beforeValues);
    if (after != null && before != null && after >= before) {
      throw new CedarAssertionException(QP_MODIFIED_BEFORE + " must be later than " + QP_MODIFIED_AFTER + ", and "
          + format(before) + " is not later than " + format(after) + ".")
          .parameter(QP_MODIFIED_AFTER, afterValues.get(0))
          .parameter(QP_MODIFIED_BEFORE, beforeValues.get(0));
    }
    return new ModifiedDateRange(after, before);
  }

  /** A bound as the instant a link to the next page carries, in UTC. */
  public static String format(long milliseconds) {
    return Instant.ofEpochMilli(milliseconds).toString();
  }

  private static Long bound(String name, List<String> values) throws CedarException {
    if (values == null || values.isEmpty()) {
      return null;
    }
    if (values.size() != 1) {
      throw new CedarAssertionException(name + " may be given once, and it was given " + values.size() + " times.")
          .parameter(name, values);
    }
    String value = values.get(0);
    Instant instant;
    try {
      instant = Instant.parse(value);
    } catch (DateTimeParseException e) {
      String reason = value.matches("-?\\d+")
          ? "' is a number. A time in epoch milliseconds or seconds is no longer accepted."
          : "' is not one.";
      throw new CedarAssertionException(name + " must be an ISO-8601 instant such as 2026-11-25T00:00:00Z, and '"
          + value + reason)
          .parameter(name, value);
    }
    try {
      // The stores hold milliseconds. Rounding a finer bound up keeps both bounds' meaning: a stored
      // millisecond is in the window exactly when its instant is.
      long milliseconds = instant.toEpochMilli();
      return instant.getNano() % 1_000_000 == 0 ? milliseconds : Math.addExact(milliseconds, 1);
    } catch (ArithmeticException | DateTimeException e) {
      throw new CedarAssertionException(name + " must be an instant CEDAR can compare, and '" + value
          + "' is too far from the present.")
          .parameter(name, value);
    }
  }
}
