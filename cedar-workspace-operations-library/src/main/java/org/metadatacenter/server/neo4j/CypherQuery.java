package org.metadatacenter.server.neo4j;

public interface CypherQuery {

  String getOriginalQuery();

  String getRunnableQuery();

}
