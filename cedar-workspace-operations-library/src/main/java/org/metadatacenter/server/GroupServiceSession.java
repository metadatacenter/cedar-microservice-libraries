package org.metadatacenter.server;

import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUsers;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;

import java.util.List;
import java.util.Map;

public interface GroupServiceSession {

  List<FolderServerGroup> findGroups();

  FolderServerGroup findGroupById(CedarGroupId groupId);

  FolderServerGroup findGroupByName(String groupName);

  FolderServerGroup createGroup(String groupName, String groupDescription);

  FolderServerGroup updateGroupById(CedarGroupId groupId, Map<NodeProperty, String> updateFields);

  boolean deleteGroupById(CedarGroupId groupId);

  CedarGroupUsers findGroupUsers(CedarGroupId groupId);

  VersionedGroupUsers findVersionedGroupUsers(CedarGroupId groupId);

  BackendCallResult<VersionedGroupUsers> updateGroupUsers(CedarGroupId groupId, CedarGroupUsersRequest request,
                                                          RevisionPrecondition precondition);

  /** Non-HTTP callers with explicit last-arrival intent may opt into wildcard replacement. */
  default BackendCallResult<VersionedGroupUsers> updateGroupUsers(CedarGroupId groupId,
                                                                  CedarGroupUsersRequest request) {
    return updateGroupUsers(groupId, request, RevisionPrecondition.any());
  }

  boolean userAdministersGroup(CedarGroupId groupId);

  long getGroupCount();
}
