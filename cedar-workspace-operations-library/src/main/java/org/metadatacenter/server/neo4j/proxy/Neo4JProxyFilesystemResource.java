package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.CedarResource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.neo4j.CypherQuery;
import org.metadatacenter.server.neo4j.CypherQueryLiteral;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.cypher.parameter.CypherParamBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderArtifact;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderFilesystemResource;
import org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderResource;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;

import java.util.List;

public class Neo4JProxyFilesystemResource extends AbstractNeo4JProxy {

  Neo4JProxyFilesystemResource(Neo4JProxies proxies, CedarConfig cedarConfig) {
    super(proxies, cedarConfig);
  }

  public FileSystemResource findResourceById(CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderResource.getResourceById();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FileSystemResource.class);
  }

  public FolderServerUser getFilesystemResourceOwner(CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderFilesystemResource.getOwner();
    CypherParameters params = CypherParamBuilderFilesystemResource.matchId(resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetOne(q, FolderServerUser.class);
  }

  List<FileSystemResource> findResourcePathById(CedarFilesystemResourceId resourceId) {
    String cypher = CypherQueryBuilderArtifact.getResourceLookupQueryById();
    CypherParameters params = CypherParamBuilderFilesystemResource.getResourceByIdParameters(proxies.pathUtil.getRootPath(), resourceId);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, FileSystemResource.class);
  }

  long findAllNodesCount() {
    String cypher = CypherQueryBuilderFilesystemResource.getAllResourceCountQuery();
    CypherQuery q = new CypherQueryLiteral(cypher);
    return executeReadGetLong(q);
  }

  List<FolderServerResourceExtract> findFilesystemResourcePathExtractById(CedarFilesystemResourceId id) {
    return findResourcePathGenericById(id, FolderServerResourceExtract.class);
  }

  private <T extends CedarResource> List<T> findResourcePathGenericById(CedarFilesystemResourceId id, Class<T> klazz) {
    String cypher = CypherQueryBuilderFilesystemResource.getResourceLookupQueryById();
    CypherParameters params = CypherParamBuilderFilesystemResource.getResourceByIdParameters(proxies.pathUtil.getRootPath(), id);
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetList(q, klazz);
  }

  public long getTotalCount(CedarResourceType resourceType) {
    String cypher = CypherQueryBuilderResource.getTotalCount(resourceType);
    CypherParameters params = new CypherParameters();
    CypherQuery q = new CypherQueryWithParameters(cypher, params);
    return executeReadGetLong(q);
  }

}
