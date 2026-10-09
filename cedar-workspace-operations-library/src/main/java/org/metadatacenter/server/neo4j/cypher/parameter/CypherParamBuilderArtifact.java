package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.util.Map;

public class CypherParamBuilderArtifact extends AbstractCypherParamBuilder {

  public static CypherParameters createArtifact(FolderServerArtifact newResource, CedarFolderId parentFolderId) {
    return createFilesystemResource(newResource, parentFolderId);
  }

  public static CypherParameters updateArtifactById(CedarArtifactId artifactId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    return updateResourceById(artifactId, updateFields, updatedBy);
  }

  public static CypherParameters updateArtifactById(CedarArtifactId artifactId,
      Map<NodeProperty, String> updateFields, CedarUserId updatedBy,
      org.metadatacenter.server.ArtifactModificationProvenance provenance) {
    CypherParameters parameters = updateResourceById(artifactId, updateFields, updatedBy);
    if (provenance != null) {
      parameters.put(NodeProperty.LAST_UPDATED_BY, provenance.modifiedBy());
      parameters.put(NodeProperty.LAST_UPDATED_ON, provenance.graphModifiedOn());
      Long epochSecond = provenance.epochSecond();
      if (epochSecond == null) parameters.put(NodeProperty.LAST_UPDATED_ON_TS, (String) null);
      else parameters.put(NodeProperty.LAST_UPDATED_ON_TS, epochSecond.longValue());
    }
    return parameters;
  }

  public static CypherParameters matchArtifactIdAndUserId(CedarArtifactId artifactId, CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ARTIFACT_ID, artifactId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters matchId(CedarArtifactId artifactId) {
    return matchResourceByIdentity(artifactId);
  }
}
