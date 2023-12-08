package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.id.*;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;

public class CypherParamBuilderFilesystemResource extends AbstractCypherParamBuilder {

  public static CypherParameters matchId(CedarResourceId resourceId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ID, resourceId);
    return params;
  }

  public static CypherParameters getResourceByIdParameters(String filesystemResourceName, CedarFilesystemResourceId resourceId) {
    return getResourceByIdentityAndName(resourceId, filesystemResourceName);
  }

  public static CypherParameters getAllResourcesLookupParameters(long limit, long offset) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    return params;
  }

  public static CypherParameters getResourceByParentIdAndName(CedarFolderId parentId, String name) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ID, parentId);
    params.put(ParameterPlaceholder.NAME, name);
    return params;
  }

  public static CypherParameters getSharedWithMeLookupParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, int limit,
                                                                 int offset, CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getSharedWithEverybodyLookupParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                                        int limit, int offset, CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getSharedWithMeCountParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                                CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getSharedWithEverybodyCountParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus,
                                                                       CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getAllLookupParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, long limit,
                                                        long offset, CedarUserId ownerId, boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters getAllCountParameters(List<CedarResourceType> resourceTypes, ResourceVersionFilter version, ResourcePublicationStatusFilter publicationStatus, CedarUserId ownerId,
                                                       boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    if (publicationStatus != null) {
      params.put(ParameterPlaceholder.PUBLICATION_STATUS, publicationStatus.getValue());
    }
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters matchId(CedarFilesystemResourceId resourceId) {
    return matchResourceByIdentity(resourceId);
  }

  public static CypherParameters getSearchIsBasedOnLookupParameters(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOnId, int limit, int offset, CedarUserId ownerId,
                                                                    boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    params.put(ParameterPlaceholder.IS_BASED_ON, isBasedOnId);
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters getSearchIsBasedOnCountParameters(List<CedarResourceType> resourceTypes, CedarTemplateId isBasedOnId, CedarUserId ownerId, boolean addPermissionConditions) {
    CypherParameters params = new CypherParameters();
    params.addResourceTypes(resourceTypes);
    params.put(ParameterPlaceholder.IS_BASED_ON, isBasedOnId);
    if (addPermissionConditions) {
      params.put(ParameterPlaceholder.USER_ID, ownerId);
    }
    return params;
  }

  public static CypherParameters matchResourceIdAndEverybodyPermission(CedarFilesystemResourceId resourceId, NodeSharePermission everybodyPermission) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.ID, resourceId);
    params.put(ParameterPlaceholder.EVERYBODY_PERMISSION, everybodyPermission.getValue());
    return params;
  }

  public static CypherParameters matchFilesystemResource(CedarFilesystemResourceId resourceId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FS_RESOURCE_ID, resourceId);
    return params;
  }

  public static CypherParameters matchFilesystemResourceAndUser(CedarFilesystemResourceId resourceId, CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FS_RESOURCE_ID, resourceId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters matchFilesystemResourceAndGroup(CedarFilesystemResourceId resourceId, CedarGroupId groupId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FS_RESOURCE_ID, resourceId);
    params.put(ParameterPlaceholder.GROUP_ID, groupId);
    return params;
  }

  public static CypherParameters getSpecialFoldersLookupParameters(int limit, int offset, CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getSpecialFoldersCountParameters(CedarUserId ownerId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

}
