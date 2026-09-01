package org.metadatacenter.server;

/** A graph resource representation and the revision read in the same Neo4j statement. */
public record VersionedResource<T>(T resource, long revision) {
}
