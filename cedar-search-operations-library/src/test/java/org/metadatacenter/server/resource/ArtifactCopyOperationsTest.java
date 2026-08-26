package org.metadatacenter.server.resource;

import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarTemplateInstanceId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.server.FolderServiceSession;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArtifactCopyOperationsTest {

  @Test
  void anOrdinaryInstanceCopyKeepsTheOriginalTemplate() throws Exception {
    FolderServiceSession folderSession = mock(FolderServiceSession.class);
    CedarTemplateInstanceId oldId = CedarTemplateInstanceId.build("instance-old");
    CedarTemplateInstanceId newId = CedarTemplateInstanceId.build("instance-new");
    CedarFolderId folderId = CedarFolderId.build("target-folder");
    CedarTemplateId originalTemplateId = CedarTemplateId.build("template-original");
    FolderServerInstance oldInstance = new FolderServerInstance();
    oldInstance.setId(oldId.getId());
    oldInstance.setIsBasedOn(originalTemplateId);
    when(folderSession.findFolderById(folderId)).thenReturn(folder(folderId));
    when(folderSession.findArtifactById(oldId)).thenReturn(oldInstance);
    when(folderSession.createResourceAsChildOfId(any(FolderServerInstance.class), any(CedarFolderId.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    FolderServerInstance copy = (FolderServerInstance) ArtifactCopyOperations.registerCopy(folderSession,
        oldId, newId, folderId, CedarResourceType.INSTANCE, "copy", "description", null, null, null);

    assertEquals(originalTemplateId, copy.getIsBasedOn());
    verify(folderSession).setDerivedFrom(newId, oldId);
  }

  @Test
  void aCloneUsesTheNewTemplateAndExplicitOwner() throws Exception {
    FolderServiceSession folderSession = mock(FolderServiceSession.class);
    CedarTemplateInstanceId oldId = CedarTemplateInstanceId.build("instance-old");
    CedarTemplateInstanceId newId = CedarTemplateInstanceId.build("instance-new");
    CedarFolderId folderId = CedarFolderId.build("target-folder");
    CedarTemplateId newTemplateId = CedarTemplateId.build("template-new");
    CedarUserId ownerId = CedarUserId.build("owner-a");
    FolderServerInstance oldInstance = new FolderServerInstance();
    oldInstance.setId(oldId.getId());
    oldInstance.setIsBasedOn(CedarTemplateId.build("template-original"));
    when(folderSession.findFolderById(folderId)).thenReturn(folder(folderId));
    when(folderSession.findArtifactById(oldId)).thenReturn(oldInstance);
    when(folderSession.createResourceAsChildOfId(any(FolderServerInstance.class), any(CedarFolderId.class),
        any(CedarUserId.class))).thenAnswer(invocation -> invocation.getArgument(0));

    FolderServerInstance clone = (FolderServerInstance) ArtifactCopyOperations.registerCopy(folderSession,
        oldId, newId, folderId, CedarResourceType.INSTANCE, "clone", "description", null,
        newTemplateId, ownerId);

    assertEquals(newTemplateId, clone.getIsBasedOn());
    verify(folderSession).createResourceAsChildOfId(clone, folderId, ownerId);
    verify(folderSession).setDerivedFrom(newId, oldId);
  }

  private static FolderServerFolder folder(CedarFolderId folderId) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setId(folderId.getId());
    return folder;
  }
}
