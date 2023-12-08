package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;

import java.util.Collection;

public class CypherParamBuilderFolderContent extends AbstractCypherParamBuilder {

  public static CypherParameters getFolderContentsFilteredCountParameters(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes, ResourcePublicationStatusFilter publicationStatus) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FOLDER_ID, folderId);
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    return params;
  }

  public static CypherParameters getFolderContentsFilteredLookupParameters(CedarFolderId folderId, Collection<CedarResourceType> resourceTypes, ResourcePublicationStatusFilter publicationStatus,
                                                                           long limit, long offset) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FOLDER_ID, folderId);
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    return params;
  }

}
