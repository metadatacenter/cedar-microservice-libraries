package org.metadatacenter.server.logging.dao;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * How times cross a native query on the log database: as text, never as {@code java.sql.Timestamp}.
 * <p>
 * The log tables' DATETIME columns hold UTC wall-clock (the entities bind their {@code Instant}s in UTC), but the
 * connection runs {@code serverTimezone=America/Los_Angeles}, and Connector/J converts a native read or a bound
 * {@code Timestamp} through that zone. A read therefore comes back shifted by the offset (seven or eight hours), and
 * a stored value inside the spring-forward gap (2027-03-14 02:00-02:59) does not exist in that zone at all, so reading
 * it throws {@code HOUR_OF_DAY: 2 -> 3}. Selecting {@link #asText} and binding {@link #bind} applies no zone anywhere:
 * MariaDB compares and stores the bound text as a DATETIME.
 */
public final class LogSqlTime {

  /** How a bound time is written: the DATETIME literal form, which MariaDB converts on comparison and insert. */
  private static final DateTimeFormatter BIND = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSS");
  /**
   * How a selected time is read back. Dots rather than colons between the time fields: these expressions go into
   * Hibernate native queries, and the query builder's contract is that no colon reaches SQL text except a bind
   * parameter (LogQueryBuilder#quote).
   */
  private static final DateTimeFormatter TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH.mm.ss.SSSSSS");

  private LogSqlTime() {
  }

  /** Select a DATETIME column (or expression) as its stored text, so that no driver time zone touches it. */
  public static String asText(String column) {
    return "DATE_FORMAT(" + column + ", '%Y-%m-%d %H.%i.%s.%f')";
  }

  /** An instant as the UTC wall-clock text the log tables store. */
  public static String bind(Instant instant) {
    return instant == null ? null : BIND.format(instant.atOffset(ZoneOffset.UTC));
  }

  /** The instant of a time selected {@link #asText}. */
  public static Instant parse(Object text) {
    return text == null ? null : LocalDateTime.parse(text.toString(), TEXT).toInstant(ZoneOffset.UTC);
  }

  /** The ISO-8601 instant of a time selected {@link #asText}, as the monitor's pages receive it. */
  public static String iso(Object text) {
    Instant instant = parse(text);
    return instant == null ? null : instant.toString();
  }
}
