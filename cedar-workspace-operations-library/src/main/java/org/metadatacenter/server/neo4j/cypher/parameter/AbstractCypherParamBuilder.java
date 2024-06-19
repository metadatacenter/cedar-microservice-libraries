package org.metadatacenter.server.neo4j.cypher.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.*;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.server.neo4j.cypher.CypherQueryParameter;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterLiteral;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public abstract class AbstractCypherParamBuilder {

  public static final String FOLDER_ALIAS_PREFIX = "f";

  protected static ParameterLiteral getFolderAlias(int i) {
    return new ParameterLiteral(FOLDER_ALIAS_PREFIX + i);
  }

  protected static CypherParameters createFilesystemResource(FileSystemResource newResource, CedarFolderId parentFolderId) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.PARENT_ID, parentFolderId);
    params.put(ParameterPlaceholder.USER_ID, newResource.getOwnedBy());

    params.put(NodeProperty.ID, newResource.getId());
    params.put(NodeProperty.NAME, newResource.getName());
    params.put(NodeProperty.NAME_LOWER, newResource.getName().toLowerCase());
    params.put(NodeProperty.DESCRIPTION, newResource.getDescription());
    params.put(NodeProperty.IDENTIFIER, newResource.getIdentifier());
    params.put(NodeProperty.CREATED_BY, newResource.getCreatedBy());
    params.put(NodeProperty.CREATED_ON, nowString);
    params.put(NodeProperty.CREATED_ON_TS, nowTS);
    params.put(NodeProperty.LAST_UPDATED_BY, newResource.getLastUpdatedBy());
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);
    params.put(NodeProperty.OWNED_BY, newResource.getOwnedBy());
    params.put(NodeProperty.RESOURCE_TYPE, newResource.getType().getValue());

    params.put(NodeProperty.IS_OPEN, newResource.isOpen());

    if (newResource.getSourceHash() != null) {
      params.put(NodeProperty.SOURCE_HASH, newResource.getSourceHash());
    }

    if (newResource instanceof FolderServerFolder newFolder) {
      params.put(NodeProperty.IS_ROOT, newFolder.isRoot());
      params.put(NodeProperty.IS_SYSTEM, newFolder.isSystem());
      params.put(NodeProperty.IS_USER_HOME, newFolder.isUserHome());
      params.put(NodeProperty.HOME_OF, newFolder.getHomeOf());
    }
    if (newResource instanceof FolderServerArtifact newArtifact) {
      if (newArtifact.getDerivedFrom() != null) {
        params.put(NodeProperty.DERIVED_FROM, newArtifact.getDerivedFrom());
      }
      if (newArtifact.getType().supportsDOI()) {
        params.put(NodeProperty.DOI, newArtifact.getDOI());
      }
    }
    if (newResource instanceof FolderServerSchemaArtifact newSchemaArtifact) {
      if (newSchemaArtifact.getVersion() != null) {
        params.put(NodeProperty.VERSION, newSchemaArtifact.getVersion());
      }
      if (newSchemaArtifact.getPublicationStatus() != null) {
        params.put(ParameterPlaceholder.PUBLICATION_STATUS, newSchemaArtifact.getPublicationStatus());
      }
      if (newSchemaArtifact.getPreviousVersion() != null) {
        params.put(ParameterPlaceholder.PREVIOUS_VERSION, newSchemaArtifact.getPreviousVersion());
      }
      if (newSchemaArtifact.isLatestVersion() != null) {
        params.put(NodeProperty.IS_LATEST_VERSION, newSchemaArtifact.isLatestVersion());
      }
      if (newSchemaArtifact.isLatestDraftVersion() != null) {
        params.put(NodeProperty.IS_LATEST_DRAFT_VERSION, newSchemaArtifact.isLatestDraftVersion());
      }
      if (newSchemaArtifact.isLatestPublishedVersion() != null) {
        params.put(NodeProperty.IS_LATEST_PUBLISHED_VERSION, newSchemaArtifact.isLatestPublishedVersion());
      }
    }
    if (newResource instanceof FolderServerInstanceArtifact) {
      FolderServerInstance newInstance = (FolderServerInstance) newResource;
      if (newInstance.getIsBasedOn() != null) {
        params.put(NodeProperty.IS_BASED_ON, newInstance.getIsBasedOn());
      }
    }
    return params;
  }

  protected static CypherParameters updateResourceById(CedarResourceId resourceId, Map<? extends CypherQueryParameter, String> updateFields, CedarUserId updatedBy) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.LAST_UPDATED_BY, updatedBy);
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);
    params.put(ParameterPlaceholder.ID, resourceId);
    for (CypherQueryParameter parameter : updateFields.keySet()) {
      params.put(parameter, updateFields.get(parameter));
    }
    return params;
  }

  protected static CypherParameters matchResourceByIdentity(CedarResourceId resourceId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ID, resourceId);
    return params;
  }

  protected static CypherParameters getResourceByIdentityAndName(CedarFilesystemResourceId folderId, String resourceName) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ID, folderId);
    params.put(ParameterPlaceholder.NAME, resourceName);
    return params;
  }

  public static CypherParameters matchFolderAndUser(CedarFolderId folderId, CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FOLDER_ID, folderId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters matchFromNodeToNode(String fromURL, String toURL) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FROM_ID, fromURL);
    params.put(ParameterPlaceholder.TO_ID, toURL);
    return params;
  }

  public static CypherParameters matchUserAndGroup(CedarUserId userId, CedarGroupId groupId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.USER_ID, userId);
    params.put(ParameterPlaceholder.GROUP_ID, groupId);
    return params;
  }

  public static CypherParameters matchFolderAndGroup(CedarFolderId folderId, CedarGroupId groupId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FOLDER_ID, folderId);
    params.put(ParameterPlaceholder.GROUP_ID, groupId);
    return params;
  }

  public static CypherParameters matchArtifactIdAndParentFolderId(CedarArtifactId artifactId, CedarFolderId parentFolderId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ARTIFACT_ID, artifactId);
    params.put(ParameterPlaceholder.PARENT_FOLDER_ID, parentFolderId);
    return params;
  }

  public static CypherParameters mapAllProperties(JsonNode node) {
    CypherParameters params = new CypherParameters();
    for (Map.Entry<String, JsonNode> entry : (Iterable<Map.Entry<String, JsonNode>>) node::fields) {
      String key = entry.getKey();
      if (key != null) {
        CypherQueryParameter param = NodeProperty.forValue(key);
        if (param != null) {
          params.put(param, entry.getValue());
        }
      }
    }
    return params;
  }

  public static CypherParameters matchSourceAndTarget(CedarResourceId sourceId, CedarResourceId targetId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.SOURCE_ID, sourceId);
    params.put(ParameterPlaceholder.TARGET_ID, targetId);
    return params;
  }

  public static CypherParameters matchUserIdAndCategoryId(CedarUserId userId, CedarCategoryId categoryId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.USER_ID, userId);
    params.put(ParameterPlaceholder.CATEGORY_ID, categoryId);
    return params;
  }

  public static CypherParameters matchGroupIdAndCategoryId(CedarGroupId groupId, CedarCategoryId categoryId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.GROUP_ID, groupId);
    params.put(ParameterPlaceholder.CATEGORY_ID, categoryId);
    return params;
  }

  public static CypherParameters matchResourceAndUser(CedarResourceId resourceId, CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.RESOURCE_ID, resourceId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters matchSourceAndTargetIds(CedarResourceId sourceId, List<String> targetIds) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.SOURCE_ID, sourceId);
    params.put(ParameterPlaceholder.TARGET_IDS, targetIds);
    return params;
  }

}
