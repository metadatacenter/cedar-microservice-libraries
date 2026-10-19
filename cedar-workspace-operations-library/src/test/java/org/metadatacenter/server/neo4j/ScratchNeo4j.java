package org.metadatacenter.server.neo4j;

import org.neo4j.configuration.GraphDatabaseInternalSettings;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Holds one in-process Neo4j, shared by every test class in this module that needs a graph.
 * Booting a harness takes several seconds, and surefire runs the module's classes one after another
 * in a single reused JVM, so one boot replaces one per class. The harness starts on first use and
 * closes when the JVM exits.
 *
 * A class finds the graph as the previous class left it, so it calls {@link #blank()} in its
 * {@code @BeforeAll}. The call returns the graph to the state of a freshly booted harness, which
 * removes any lock, constraint or index another class created. The class then runs its own
 * initializers, VersionChainTransaction's lifecycle lock among them. No test may stop this harness.
 * A test that needs a real outage boots a private one.
 */
public final class ScratchNeo4j {

  private static Neo4j neo4j;

  private ScratchNeo4j() {
  }

  public static synchronized URI boltURI() {
    return harness().boltURI();
  }

  /** Deletes every node, and leaves the constraints and indexes in place. */
  public static synchronized void clear() {
    harness().defaultDatabaseService().executeTransactionally("MATCH (n) DETACH DELETE n");
  }

  /** Deletes every node, then drops every constraint and every index but the built-in token lookups. */
  public static synchronized void blank() {
    clear();
    GraphDatabaseService database = harness().defaultDatabaseService();
    for (String constraint : names(database, "SHOW CONSTRAINTS YIELD name RETURN name")) {
      database.executeTransactionally("DROP CONSTRAINT `" + constraint + "`");
    }
    for (String index : names(database, "SHOW INDEXES YIELD name, type WHERE type <> 'LOOKUP' RETURN name")) {
      database.executeTransactionally("DROP INDEX `" + index + "`");
    }
  }

  private static Neo4j harness() {
    if (neo4j == null) {
      // Closing the harness shuts Bolt's Netty event-loop group down gracefully: every loop closes
      // its connections, then all of them wait out a five-second quiet period together, so a close
      // took about six seconds. The connections are already closed when the period starts, so a
      // zero period changes nothing a client can observe, and the close takes under one second.
      Neo4j started = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
          .withConfig(GraphDatabaseInternalSettings.netty_server_shutdown_quiet_period, 0).build();
      Runtime.getRuntime().addShutdownHook(new Thread(started::close));
      neo4j = started;
    }
    return neo4j;
  }

  private static List<String> names(GraphDatabaseService database, String query) {
    return database.executeTransactionally(query, Map.of(),
        result -> result.stream().map(row -> (String) row.get("name")).toList());
  }
}
