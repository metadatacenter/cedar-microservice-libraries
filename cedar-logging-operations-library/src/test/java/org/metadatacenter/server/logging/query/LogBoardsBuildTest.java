package org.metadatacenter.server.logging.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.logging.query.LogBoards.Board;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * Every board in the catalog must translate to SQL. The catalog is server-owned and the UI renders
 * whatever it lists, so a board whose spec the builder rejects is a menu entry that answers with a
 * 400 — which is exactly how traffic-overview and traffic-per-hour shipped broken.
 */
class LogBoardsBuildTest {

  private static final String FROM = "2026-09-14T00:00:00Z";
  private static final String TO = "2026-09-15T00:00:00Z";

  /** The catalog carries no range; the page supplies one. Re-spec each board with a concrete range. */
  private static LogQuerySpec withRange(LogQuerySpec s) {
    return new LogQuerySpec(s.table(), FROM, TO, s.filters(), s.groupBy(), s.metrics(), s.orderBy(),
        s.limit(), s.cursor(), s.having(), s.source());
  }

  @Test
  void everyBoardInTheCatalogBuilds() {
    List<String> failures = new ArrayList<>();
    for (Board b : LogBoards.all()) {
      if (b.spec() == null) {
        continue;   // endpoint-backed boards join the two tables and are not specs
      }
      try {
        LogQueryBuilder.build(withRange(b.spec()));
      } catch (RuntimeException e) {
        failures.add(b.id() + "  ->  " + e.getClass().getSimpleName() + ": " + e.getMessage());
      }
    }
    if (!failures.isEmpty()) {
      fail("Boards that do not build (" + failures.size() + "):\n  " + String.join("\n  ", failures));
    }
  }
}
