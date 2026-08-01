package org.metadatacenter.server.resource;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.server.FolderServiceSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloneInstancesExecutorServiceTest {

  @Test
  void loadsEveryInstanceAcrossRepositoryPageBoundariesInStableOrder() {
    CedarTemplateId templateId = CedarTemplateId.build("template-old");
    List<FolderServerResourceExtract> stored = IntStream.range(0, 2501)
        .mapToObj(CloneInstancesExecutorServiceTest::instance)
        .toList();
    List<Integer> requestedOffsets = new ArrayList<>();
    FolderServiceSession repository = mock(FolderServiceSession.class);
    when(repository.searchIsBasedOn(any(), any(), anyInt(), anyInt(), any())).thenAnswer(invocation -> {
      int limit = invocation.getArgument(2);
      int offset = invocation.getArgument(3);
      requestedOffsets.add(offset);
      return stored.subList(offset, Math.min(offset + limit, stored.size()));
    });

    List<FolderServerResourceExtract> loaded =
        CloneInstancesExecutorService.findAllInstances(repository, templateId);

    assertEquals(2501, loaded.size());
    assertEquals(List.of("instance-0", "instance-1000", "instance-2000", "instance-2500"),
        List.of(loaded.get(0).getId(), loaded.get(1000).getId(), loaded.get(2000).getId(), loaded.get(2500).getId()));
    assertEquals(List.of(0, 1000, 2000), requestedOffsets);
  }

  @Test
  void ownerlessLegacyInstanceDoesNotPreventValidOwnerBatchesFromBeingCloned() {
    FolderServerResourceExtract ownerAFirst = instance(1); ownerAFirst.setOwnedBy("owner-a");
    FolderServerResourceExtract missingOwner = instance(2); missingOwner.setOwnedBy(null);
    FolderServerResourceExtract ownerB = instance(3); ownerB.setOwnedBy("owner-b");
    FolderServerResourceExtract blankOwner = instance(4); blankOwner.setOwnedBy("  ");
    FolderServerResourceExtract ownerASecond = instance(5); ownerASecond.setOwnedBy("owner-a");

    Map<String, List<FolderServerResourceExtract>> grouped =
        CloneInstancesExecutorService.groupInstancesByOwner(
            List.of(ownerAFirst, missingOwner, ownerB, blankOwner, ownerASecond));

    assertEquals(List.of("owner-a", "owner-b"), new ArrayList<>(grouped.keySet()));
    assertEquals(List.of("instance-1", "instance-5"),
        grouped.get("owner-a").stream().map(FolderServerResourceExtract::getId).toList());
    assertEquals(List.of("instance-3"),
        grouped.get("owner-b").stream().map(FolderServerResourceExtract::getId).toList());
  }

  private static FolderServerResourceExtract instance(int index) {
    FolderServerResourceExtract instance = FolderServerResourceExtract.forType(CedarResourceType.INSTANCE);
    instance.setId("instance-" + index);
    instance.setOwnedBy("owner-" + (index % 3));
    return instance;
  }
}
