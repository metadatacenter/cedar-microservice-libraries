package org.metadatacenter.server;

import org.metadatacenter.server.security.model.permission.category.CategoryPermissions;

public record VersionedCategoryPermissions(CategoryPermissions content, long revision) {
}
