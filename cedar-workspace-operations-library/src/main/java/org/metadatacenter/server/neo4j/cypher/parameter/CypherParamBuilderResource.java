package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

public class CypherParamBuilderResource extends AbstractCypherParamBuilder {

  public static CypherParameters matchId(CedarResourceId id) {
    return matchResourceByIdentity(id);
  }
}
