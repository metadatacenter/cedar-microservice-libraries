package org.metadatacenter.server;

import org.metadatacenter.server.security.model.auth.CedarNodePermissionsWithExtract;

public record VersionedResourcePermissions(CedarNodePermissionsWithExtract content, long revision) {
}
