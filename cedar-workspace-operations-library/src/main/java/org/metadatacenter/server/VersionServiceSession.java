package org.metadatacenter.server;

import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;

public interface VersionServiceSession {

  OutcomeWithReason userCanPerformVersioning(FilesystemResourceWithCurrentUserPermissions resource);

  OutcomeWithReason resourceCanBePublished(FilesystemResourceWithCurrentUserPermissions resource);

  OutcomeWithReason resourceCanBeDrafted(FilesystemResourceWithCurrentUserPermissions resource);
}
