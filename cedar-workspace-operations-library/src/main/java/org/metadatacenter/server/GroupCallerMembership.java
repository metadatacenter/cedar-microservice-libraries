package org.metadatacenter.server;

/** The authenticated caller's own relationships, not their effective system permissions. */
public record GroupCallerMembership(boolean member, boolean administrator) {
}
