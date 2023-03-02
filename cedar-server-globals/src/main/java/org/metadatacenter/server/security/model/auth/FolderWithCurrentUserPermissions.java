package org.metadatacenter.server.security.model.auth;

import org.metadatacenter.id.CedarFolderId;

public interface FolderWithCurrentUserPermissions<T extends CedarFolderId> extends FilesystemResourceWithCurrentUserPermissions<T> {

  boolean isRoot();

  boolean isSystem();

  boolean isUserHome();

  boolean isOpen();
}
