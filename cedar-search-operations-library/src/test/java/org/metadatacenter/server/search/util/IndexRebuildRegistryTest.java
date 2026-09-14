package org.metadatacenter.server.search.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexRebuildRegistryTest {

  private static final String NEW_INDEX = "cedar-search-2026-09-14t030000";
  private static final Set<String> SNAPSHOT = Set.of("a", "b", "c");

  @AfterEach
  void tearDown() {
    IndexRebuildRegistry.end(NEW_INDEX);
    IndexRebuildRegistry.end("other-index");
  }

  @Test
  void nothingIsMirroredWhenNoRebuildIsRunning() {
    assertTrue(IndexRebuildRegistry.inProgressIndex().isEmpty());

    IndexRebuildRegistry.recordLiveWrite("a");

    assertFalse(IndexRebuildRegistry.wasWrittenLive("a"), "there is no index to have written it into");
  }

  @Test
  void aRebuildAnnouncesWhereLiveWritesShouldGo() {
    IndexRebuildRegistry.begin(NEW_INDEX);

    assertEquals(NEW_INDEX, IndexRebuildRegistry.inProgressIndex().orElseThrow());
  }

  @Test
  void aResourceWrittenLiveIsLeftAloneByTheRebuild() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("b");

    assertTrue(IndexRebuildRegistry.wasWrittenLive("b"));
    assertFalse(IndexRebuildRegistry.wasWrittenLive("a"));
  }

  @Test
  void anEditDuringTheRebuildChangesNoCount() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("b");

    assertEquals(0, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT),
        "an edited resource is in the work list and in the new index");
  }

  @Test
  void aCreateDuringTheRebuildAddsOne() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("brand-new");

    assertEquals(1, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT),
        "it is in the new index but was never in the work list");
  }

  @Test
  void aDeleteDuringTheRebuildTakesOneAway() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveDelete("c");

    assertEquals(-1, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT),
        "it is in the work list but must not be in the new index");
  }

  @Test
  void aResourceCreatedAndThenDeletedDuringTheRebuildChangesNoCount() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("brand-new");
    IndexRebuildRegistry.recordLiveDelete("brand-new");

    assertEquals(0, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT),
        "it is in neither the work list nor the new index");
    assertTrue(IndexRebuildRegistry.wasWrittenLive("brand-new"), "the rebuild must still not write it");
  }

  @Test
  void aResourceDeletedAndThenRecreatedCountsAsACreate() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveDelete("brand-new");
    IndexRebuildRegistry.recordLiveWrite("brand-new");

    assertEquals(1, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT));
  }

  @Test
  void aDeletedThenRecreatedSnapshotResourceChangesNoCount() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveDelete("a");
    IndexRebuildRegistry.recordLiveWrite("a");

    assertEquals(0, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT),
        "it is back, and it was in the work list all along");
  }

  @Test
  void severalCreatesAndDeletesNetOut() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("new-1");
    IndexRebuildRegistry.recordLiveWrite("new-2");
    IndexRebuildRegistry.recordLiveWrite("b");
    IndexRebuildRegistry.recordLiveDelete("a");

    assertEquals(1, IndexRebuildRegistry.expectedCountAdjustment(SNAPSHOT), "+2 created, -1 deleted");
  }

  @Test
  void endingTheRebuildStopsTheMirroringAndForgetsWhatItTracked() {
    IndexRebuildRegistry.begin(NEW_INDEX);
    IndexRebuildRegistry.recordLiveWrite("b");

    IndexRebuildRegistry.end(NEW_INDEX);

    assertTrue(IndexRebuildRegistry.inProgressIndex().isEmpty());
    assertFalse(IndexRebuildRegistry.wasWrittenLive("b"));
  }

  @Test
  void anOldJobCannotStopTheMirroringANewerOneDependsOn() {
    IndexRebuildRegistry.begin("other-index");
    IndexRebuildRegistry.begin(NEW_INDEX);

    // The first rebuild failed and only now reaches its finally block.
    IndexRebuildRegistry.end("other-index");

    assertEquals(NEW_INDEX, IndexRebuildRegistry.inProgressIndex().orElseThrow(),
        "the rebuild that is actually running must keep receiving live writes");
  }

  @Test
  void aNewRebuildDoesNotInheritTheLastOnesTracking() {
    IndexRebuildRegistry.begin("other-index");
    IndexRebuildRegistry.recordLiveWrite("b");

    IndexRebuildRegistry.begin(NEW_INDEX);

    assertFalse(IndexRebuildRegistry.wasWrittenLive("b"),
        "this rebuild has written nothing yet, so it must write every resource in its work list");
  }
}
