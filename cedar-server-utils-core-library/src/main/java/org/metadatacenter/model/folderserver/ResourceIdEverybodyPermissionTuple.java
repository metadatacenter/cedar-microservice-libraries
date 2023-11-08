package org.metadatacenter.model.folderserver;

import org.metadatacenter.id.CedarUntypedResourceId;
import org.metadatacenter.model.folderserver.result.ResultTuple;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;

public class ResourceIdEverybodyPermissionTuple extends ResultTuple {

  private CedarUntypedResourceId resourceId;
  private NodeSharePermission everybodyPermission;


  public CedarUntypedResourceId getResourceId() {
    return resourceId;
  }

  public NodeSharePermission getEverybodyPermission() {
    return everybodyPermission;
  }
}
