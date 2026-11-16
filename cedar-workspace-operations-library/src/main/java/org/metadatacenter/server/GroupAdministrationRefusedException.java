package org.metadatacenter.server;

import org.metadatacenter.id.CedarGroupId;

/**
 * Raised when a caller who may not administer a group asks to change or delete it. The group exists,
 * so the refusal is one of permission, which the servers answer with 403. A group that does not exist
 * is not refused this way: the write reports that nothing was found, as it always has.
 */
public class GroupAdministrationRefusedException extends RuntimeException {

  private final transient CedarGroupId groupId;

  public GroupAdministrationRefusedException(CedarGroupId groupId) {
    super("Only the administrators of the group may change it: " + (groupId == null ? null : groupId.getId()));
    this.groupId = groupId;
  }

  public CedarGroupId getGroupId() {
    return groupId;
  }
}
