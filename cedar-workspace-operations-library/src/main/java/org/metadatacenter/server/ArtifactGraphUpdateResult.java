package org.metadatacenter.server;

import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;

/** Distinguishes a superseded successful write from a write undone by compensation. */
public record ArtifactGraphUpdateResult(FolderServerArtifact resource, Outcome outcome) {
  public enum Outcome { UPDATED, SUPERSEDED, RESTORED, FAILED }

  public static ArtifactGraphUpdateResult updated(FolderServerArtifact resource) {
    return new ArtifactGraphUpdateResult(resource, resource == null ? Outcome.FAILED : Outcome.UPDATED);
  }
}
