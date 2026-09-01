package org.metadatacenter.server;

import org.metadatacenter.server.security.model.auth.CedarGroupUsers;

/** One group-membership representation paired with the revision read in the same transaction. */
public record VersionedGroupUsers(CedarGroupUsers content, long revision) {
}
