package org.metadatacenter.server.resource;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.ArtifactCreateCleanupOutbox.Stored;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** How the relay reads the artifact server's answer about a create's identity. */
class ArtifactCreateCleanupServiceTest {

  @Test
  void aStoredDocumentIsTakenAtTheDatastoreRevisionItsValidatorNames() {
    assertEquals(new Stored.Revision("\"7\""), ArtifactCreateCleanupService.stored(200, "\"7\""));
    assertEquals(new Stored.Revision("\"7\""), ArtifactCreateCleanupService.stored(200, "\"7--gzip\""));
  }

  @Test
  void aDocumentWhoseValidatorNamesNoSingleRevisionCannotBeDeleted() {
    for (String validator : new String[]{null, "*", "W/\"7\"", "\"7\", \"8\"", "\"seven\""}) {
      assertEquals(new Stored.Unversioned(), ArtifactCreateCleanupService.stored(200, validator), validator);
    }
  }

  @Test
  void onlyANotFoundAnswerSaysNothingIsStored() {
    assertEquals(new Stored.Nothing(), ArtifactCreateCleanupService.stored(404, null));
    for (int status : new int[]{401, 403, 410, 500, 503}) {
      assertEquals(new Stored.Unknown(status), ArtifactCreateCleanupService.stored(status, "\"7\""));
    }
  }
}
