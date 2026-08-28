package org.metadatacenter.server.resource;

import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.url.MicroserviceUrlUtil;

import jakarta.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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

  @Test
  void aPerInstanceFailureFailsTheJobAfterTheRemainingInstancesAreAttempted() {
    CedarTemplateId oldTemplateId = CedarTemplateId.build("template-old");
    CedarTemplateId newTemplateId = CedarTemplateId.build("template-new");
    CedarUserId ownerId = CedarUserId.build("owner-a");
    CedarFolderId homeFolderId = CedarFolderId.build("home-folder");
    CedarFolderId targetFolderId = CedarFolderId.build("target-folder");

    FolderServerResourceExtract failing = instance(1); failing.setOwnedBy(ownerId.getId());
    FolderServerResourceExtract succeeding = instance(2); succeeding.setOwnedBy(ownerId.getId());
    FolderServiceSession repository = mock(FolderServiceSession.class);
    when(repository.getNumberOfInstances(oldTemplateId)).thenReturn(2L);
    when(repository.searchIsBasedOn(any(), any(), anyInt(), anyInt(), any()))
        .thenReturn(List.of(failing, succeeding));

    FolderServerFolder homeFolder = new FolderServerFolder();
    homeFolder.setId(homeFolderId.getId());
    when(repository.findHomeFolderOfUser(ownerId)).thenReturn(homeFolder);
    FolderServerTemplate newTemplate = new FolderServerTemplate();
    newTemplate.setName("New template");
    newTemplate.setVersion("2.0.0");
    when(repository.findResourceById(newTemplateId)).thenReturn(newTemplate);
    FolderServerFolder targetFolder = new FolderServerFolder();
    targetFolder.setId(targetFolderId.getId());
    when(repository.createFolderAsChildOfId(any(FolderServerFolder.class), any(CedarFolderId.class),
        any(CedarFolderId.class), any(CedarUserId.class))).thenReturn(targetFolder);

    LinkedDataUtil linkedDataUtil = mock(LinkedDataUtil.class);
    when(linkedDataUtil.buildNewLinkedDataIdObject(CedarFolderId.class)).thenReturn(targetFolderId);
    AtomicInteger attempts = new AtomicInteger();
    CloneInstancesExecutorService service = new CloneInstancesExecutorService(repository,
        mock(CedarRequestContext.class), mock(MicroserviceUrlUtil.class), linkedDataUtil) {
      @Override
      protected Response copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId oldInstanceId,
                                                             CedarTemplateId ignoredNewTemplateId,
                                                             CedarFolderId ignoredDestinationFolderId,
                                                             CedarUserId ignoredUserId) throws CedarProcessingException {
        attempts.incrementAndGet();
        if (oldInstanceId.getId().equals(failing.getId())) {
          throw new CedarProcessingException("validation failed");
        }
        return Response.ok().build();
      }
    };

    // Not retryable: the succeeding instance was cloned, so a re-run would duplicate it, and the
    // failing instance fails the same way every time.
    CloneInstancesNotRetryableException error = assertThrows(CloneInstancesNotRetryableException.class,
        () -> service.handleEvent(new CloneInstancesQueueEvent(oldTemplateId, newTemplateId, null)));

    assertEquals(2, attempts.get());
    assertTrue(error.getMessage().contains(failing.getId()));
  }

  @Test
  void aFailureBeforeAnythingIsClonedStaysRetryable() {
    CedarTemplateId oldTemplateId = CedarTemplateId.build("template-old");
    CedarTemplateId newTemplateId = CedarTemplateId.build("template-new");
    CedarUserId ownerId = CedarUserId.build("owner-a");

    FolderServerResourceExtract instance = instance(1);
    instance.setOwnedBy(ownerId.getId());
    FolderServiceSession repository = mock(FolderServiceSession.class);
    when(repository.getNumberOfInstances(oldTemplateId)).thenReturn(1L);
    when(repository.searchIsBasedOn(any(), any(), anyInt(), anyInt(), any())).thenReturn(List.of(instance));
    FolderServerFolder homeFolder = new FolderServerFolder();
    homeFolder.setId(CedarFolderId.build("home-folder").getId());
    when(repository.findHomeFolderOfUser(ownerId)).thenReturn(homeFolder);
    FolderServerTemplate newTemplate = new FolderServerTemplate();
    newTemplate.setName("New template");
    newTemplate.setVersion("2.0.0");
    when(repository.findResourceById(newTemplateId)).thenReturn(newTemplate);
    // The very first mutation fails, so nothing exists that a re-run would duplicate.
    when(repository.createFolderAsChildOfId(any(FolderServerFolder.class), any(CedarFolderId.class),
        any(CedarFolderId.class), any(CedarUserId.class))).thenThrow(new IllegalStateException("graph unreachable"));
    LinkedDataUtil linkedDataUtil = mock(LinkedDataUtil.class);
    when(linkedDataUtil.buildNewLinkedDataIdObject(CedarFolderId.class))
        .thenReturn(CedarFolderId.build("target-folder"));

    CloneInstancesExecutorService service = new CloneInstancesExecutorService(repository,
        mock(CedarRequestContext.class), mock(MicroserviceUrlUtil.class), linkedDataUtil);

    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> service.handleEvent(new CloneInstancesQueueEvent(oldTemplateId, newTemplateId, null)));
    assertFalse(error instanceof CloneInstancesNotRetryableException,
        "nothing was created, so the queue processor may retry");
  }

  @Test
  void aCrashAfterProgressIsNotRetryable() {
    CedarTemplateId oldTemplateId = CedarTemplateId.build("template-old");
    CedarTemplateId newTemplateId = CedarTemplateId.build("template-new");
    CedarUserId ownerA = CedarUserId.build("owner-a");
    CedarUserId ownerB = CedarUserId.build("owner-b");

    FolderServerResourceExtract first = instance(1); first.setOwnedBy(ownerA.getId());
    FolderServerResourceExtract second = instance(2); second.setOwnedBy(ownerB.getId());
    FolderServiceSession repository = mock(FolderServiceSession.class);
    when(repository.getNumberOfInstances(oldTemplateId)).thenReturn(2L);
    when(repository.searchIsBasedOn(any(), any(), anyInt(), anyInt(), any())).thenReturn(List.of(first, second));
    FolderServerFolder homeFolder = new FolderServerFolder();
    homeFolder.setId(CedarFolderId.build("home-folder").getId());
    when(repository.findHomeFolderOfUser(any(CedarUserId.class))).thenReturn(homeFolder);
    FolderServerTemplate newTemplate = new FolderServerTemplate();
    newTemplate.setName("New template");
    newTemplate.setVersion("2.0.0");
    when(repository.findResourceById(newTemplateId)).thenReturn(newTemplate);
    FolderServerFolder targetFolder = new FolderServerFolder();
    targetFolder.setId(CedarFolderId.build("target-folder").getId());
    // Owner A's folder is created and their instance cloned; owner B's folder creation dies.
    when(repository.createFolderAsChildOfId(any(FolderServerFolder.class), any(CedarFolderId.class),
        any(CedarFolderId.class), any(CedarUserId.class)))
        .thenReturn(targetFolder)
        .thenThrow(new IllegalStateException("graph unreachable"));
    LinkedDataUtil linkedDataUtil = mock(LinkedDataUtil.class);
    when(linkedDataUtil.buildNewLinkedDataIdObject(CedarFolderId.class))
        .thenReturn(CedarFolderId.build("target-folder"));

    CloneInstancesExecutorService service = new CloneInstancesExecutorService(repository,
        mock(CedarRequestContext.class), mock(MicroserviceUrlUtil.class), linkedDataUtil) {
      @Override
      protected Response copyInstanceToFolderWithNewTemplate(CedarTemplateInstanceId ignoredOldInstanceId,
                                                             CedarTemplateId ignoredNewTemplateId,
                                                             CedarFolderId ignoredDestinationFolderId,
                                                             CedarUserId ignoredUserId) {
        return Response.ok().build();
      }
    };

    assertThrows(CloneInstancesNotRetryableException.class,
        () -> service.handleEvent(new CloneInstancesQueueEvent(oldTemplateId, newTemplateId, null)));
  }

  @Test
  void ownerlessInstancesFailTheJobWithoutInvitingARetry() {
    CedarTemplateId oldTemplateId = CedarTemplateId.build("template-old");
    CedarTemplateId newTemplateId = CedarTemplateId.build("template-new");

    FolderServerResourceExtract ownerless = instance(1);
    ownerless.setOwnedBy(null);
    FolderServiceSession repository = mock(FolderServiceSession.class);
    when(repository.getNumberOfInstances(oldTemplateId)).thenReturn(1L);
    when(repository.searchIsBasedOn(any(), any(), anyInt(), anyInt(), any())).thenReturn(List.of(ownerless));

    CloneInstancesExecutorService service = new CloneInstancesExecutorService(repository,
        mock(CedarRequestContext.class), mock(MicroserviceUrlUtil.class), mock(LinkedDataUtil.class));

    // Deterministic per-instance failures: a retry reproduces them, so the job dead-letters once.
    CloneInstancesNotRetryableException error = assertThrows(CloneInstancesNotRetryableException.class,
        () -> service.handleEvent(new CloneInstancesQueueEvent(oldTemplateId, newTemplateId, null)));
    assertTrue(error.getMessage().contains(ownerless.getId()));
  }

  private static FolderServerResourceExtract instance(int index) {
    FolderServerResourceExtract instance = FolderServerResourceExtract.forType(CedarResourceType.INSTANCE);
    instance.setId("instance-" + index);
    instance.setOwnedBy("owner-" + (index % 3));
    return instance;
  }
}
