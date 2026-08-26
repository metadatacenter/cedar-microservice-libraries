package org.metadatacenter.server.dao;

/** Public artifact content paired with the storage revision read from the same Mongo document. */
public record ArtifactWithRevision<T>(T content, long revision) {
}
