package org.metadatacenter.server.security.model.auth;

import org.metadatacenter.server.security.model.NodeWithIdAndType;

public interface NodeWithCurrentUserPermissions extends NodeWithIdAndType {

  CurrentUserPermissions getCurrentUserPermissions();
}
