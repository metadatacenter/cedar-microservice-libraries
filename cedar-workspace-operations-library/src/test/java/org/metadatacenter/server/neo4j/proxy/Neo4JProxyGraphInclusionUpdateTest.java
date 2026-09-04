package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.server.neo4j.CypherQuery;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class Neo4JProxyGraphInclusionUpdateTest {

  @Test
  void replacementRunsDeleteAndCreateInOneBatch() {
    CapturingGraph graph = new CapturingGraph();

    assertTrue(graph.updateInclusionArcs(CedarTemplateId.build("template-1"), List.of("field-1")));

    assertEquals(2, graph.queries.size());
    assertTrue(graph.queries.get(0).getRunnableQuery().contains("DELETE r"));
    assertTrue(graph.queries.get(1).getRunnableQuery().contains("CREATE (s)-"));
    assertEquals("updating inclusion arcs", graph.eventDescription);
  }

  @Test
  void removingEveryArcUsesTheSameBatchBoundary() {
    CapturingGraph graph = new CapturingGraph();

    assertTrue(graph.updateInclusionArcs(CedarTemplateId.build("template-1"), List.of()));

    assertEquals(1, graph.queries.size());
    assertTrue(graph.queries.get(0).getRunnableQuery().contains("DELETE r"));
    assertEquals("updating inclusion arcs", graph.eventDescription);
  }

  private static final class CapturingGraph extends Neo4JProxyGraph {
    private List<CypherQuery> queries;
    private String eventDescription;

    private CapturingGraph() {
      super(mock(Neo4JProxies.class), mock(CedarConfig.class));
    }

    @Override
    protected boolean executeWriteBatch(List<CypherQuery> queries, String eventDescription) {
      this.queries = queries;
      this.eventDescription = eventDescription;
      return true;
    }
  }
}
