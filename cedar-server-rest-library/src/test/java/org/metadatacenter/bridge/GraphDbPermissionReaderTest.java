package org.metadatacenter.bridge;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarObjectNotFoundException;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerArtifactCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerCategoryCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerFolderCurrentUserReport;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerTemplateCurrentUserReport;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.CategoryPermissionServiceSession;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.security.model.permission.category.CategoryAuthority;
import org.metadatacenter.server.security.model.permission.category.CategoryCapability;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;
import org.metadatacenter.server.security.model.permission.resource.ResourceAuthority;
import org.metadatacenter.server.security.model.permission.resource.ResourceCapability;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The current-user report a server returns for one artifact, folder or category, and on which the
 * resource server bases its category capability gate. The capability rules themselves are tested
 * with the updaters in the workspace library. What is asserted here is the assembly: the report is
 * the requested resource, its permissions are the ones the graph grants for that resource's own
 * identifier, and a resource that does not exist is refused before any permission is read.
 */
class GraphDbPermissionReaderTest {

  private static final String TEMPLATE_ID = "https://repo.metadatacenter.org/templates/1d2c4a8e-5d1b-4d4f-9e1a-2f6c0b9c7a11";
  private static final String FOLDER_ID = "https://repo.metadatacenter.org/folders/7b0e3a52-0f6e-4f53-9c3e-8a1d2e4b6c70";
  private static final String PARENT_ID = "https://repo.metadatacenter.org/folders/0c9d8e7f-6a5b-4c3d-8e2f-1a0b9c8d7e6f";
  private static final String CATEGORY_ID = "https://repo.metadatacenter.org/categories/3e4f5a6b-7c8d-4e9f-a0b1-c2d3e4f5a6b7";

  private CedarRequestContext context;
  private FolderServiceSession folderSession;
  private ResourcePermissionServiceSession permissionSession;
  private FolderServerFolderExtract parent;

  @BeforeEach
  void setUp() {
    context = mock(CedarRequestContext.class);
    when(context.getCedarUser()).thenReturn(mock(CedarUser.class));
    folderSession = mock(FolderServiceSession.class);
    permissionSession = mock(ResourcePermissionServiceSession.class);
    parent = new FolderServerFolderExtract();
    parent.setId(PARENT_ID);
    when(permissionSession.getResourceAuthority(parent.getResourceId())).thenReturn(new ResourceAuthority(null, false));
    when(folderSession.findNodePathExtract(any())).thenReturn(List.of(parent));
  }

  @Test
  void aMissingResourceIsNotFoundUnderItsOwnKeyBeforeAnyPermissionIsRead() {
    CategoryServiceSession categorySession = mock(CategoryServiceSession.class);
    CategoryPermissionServiceSession categoryPermissionSession = mock(CategoryPermissionServiceSession.class);

    CedarException artifact = assertThrows(CedarObjectNotFoundException.class, () ->
        GraphDbPermissionReader.getArtifactCurrentUserReport(context, folderSession, permissionSession,
            CedarTemplateId.build(TEMPLATE_ID)));
    CedarException folder = assertThrows(CedarObjectNotFoundException.class, () ->
        GraphDbPermissionReader.getFolderCurrentUserReport(context, folderSession, permissionSession,
            CedarFolderId.build(FOLDER_ID)));
    CedarException category = assertThrows(CedarObjectNotFoundException.class, () ->
        GraphDbPermissionReader.getCategoryCurrentUserReport(categorySession, categoryPermissionSession,
            CedarCategoryId.build(CATEGORY_ID)));

    assertSame(CedarErrorKey.ARTIFACT_NOT_FOUND, artifact.getErrorPack().getErrorKey());
    assertSame(CedarErrorKey.FOLDER_NOT_FOUND, folder.getErrorPack().getErrorKey());
    assertSame(CedarErrorKey.CATEGORY_NOT_FOUND, category.getErrorPack().getErrorKey());
    verifyNoInteractions(permissionSession, categoryPermissionSession);
  }

  /** The resource server turns a null category report into its own 404, so null must mean "no id". */
  @Test
  void noIdentifierYieldsNoReportAndReadsNothing() throws Exception {
    CategoryServiceSession categorySession = mock(CategoryServiceSession.class);
    CategoryPermissionServiceSession categoryPermissionSession = mock(CategoryPermissionServiceSession.class);

    assertNull(GraphDbPermissionReader.getArtifactCurrentUserReport(context, folderSession, permissionSession, null));
    assertNull(GraphDbPermissionReader.getFolderCurrentUserReport(context, folderSession, permissionSession,
        (CedarFolderId) null));
    assertNull(GraphDbPermissionReader.getCategoryCurrentUserReport(categorySession, categoryPermissionSession, null));
    verifyNoInteractions(folderSession, permissionSession, categorySession, categoryPermissionSession);
  }

  @Test
  void anArtifactReportCarriesWhatTheGraphGrantsOnThatArtifact() throws Exception {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(TEMPLATE_ID);
    template.setName("Study");
    CedarTemplateId id = CedarTemplateId.build(TEMPLATE_ID);
    when(folderSession.findArtifactById(id)).thenReturn(template);
    ResourceAuthority viewer = new ResourceAuthority(ResourceRole.VIEWER, false);
    when(permissionSession.getResourceAuthority(template.getResourceId())).thenReturn(viewer);
    when(permissionSession.getResourceCapabilities(template.getResourceId()))
        .thenReturn(viewer.capabilitiesFor(CedarResourceType.TEMPLATE));
    VersionServiceSession versionSession = mock(VersionServiceSession.class);
    when(versionSession.userCanPerformVersioning(any()))
        .thenReturn(OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_BY_OWNER));
    CedarDataServices dataServices = mock(CedarDataServices.class);
    when(dataServices.getVersionServiceSession(context)).thenReturn(versionSession);

    FolderServerArtifactCurrentUserReport report;
    try (MockedStatic<CedarDataServices> services = mockStatic(CedarDataServices.class)) {
      services.when(CedarDataServices::getInstance).thenReturn(dataServices);
      report = GraphDbPermissionReader.getArtifactCurrentUserReport(context, folderSession, permissionSession, id);
    }

    assertInstanceOf(FolderServerTemplateCurrentUserReport.class, report);
    assertEquals(TEMPLATE_ID, report.getId());
    assertEquals("Study", report.getName());
    assertSame(ResourceRole.VIEWER, report.getCurrentUserPermissions().getRole());
    assertEquals(viewer.capabilitiesFor(CedarResourceType.TEMPLATE), report.getCurrentUserPermissions().getCapabilities());
    assertFalse(report.getCurrentUserPermissions().getCapabilities().contains(ResourceCapability.UPDATE_RESOURCE));
    assertSame(CedarErrorKey.VERSIONING_ONLY_BY_OWNER, report.getCurrentUserPermissions().getPublishErrorKey());
    assertEquals(List.of(PARENT_ID), report.getPathInfo().stream().map(FolderServerResourceExtract::getId).toList());
  }

  @Test
  void aFolderReportCarriesWhatTheGraphGrantsOnThatFolder() throws Exception {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setId(FOLDER_ID);
    ResourceAuthority manager = new ResourceAuthority(ResourceRole.MANAGER, false);
    when(permissionSession.getResourceAuthority(folder.getResourceId())).thenReturn(manager);
    when(permissionSession.getResourceCapabilities(folder.getResourceId()))
        .thenReturn(manager.capabilitiesFor(CedarResourceType.FOLDER));

    FolderServerFolderCurrentUserReport report =
        GraphDbPermissionReader.getFolderCurrentUserReport(context, folderSession, permissionSession, folder);

    assertEquals(FOLDER_ID, report.getId());
    assertSame(ResourceRole.MANAGER, report.getCurrentUserPermissions().getRole());
    assertEquals(manager.capabilitiesFor(CedarResourceType.FOLDER), report.getCurrentUserPermissions().getCapabilities());
    assertEquals(List.of(PARENT_ID), report.getPathInfo().stream().map(FolderServerResourceExtract::getId).toList());
  }

  @Test
  void aCategoryReportCarriesWhatTheGraphGrantsOnThatCategory() throws Exception {
    FolderServerCategory category = new FolderServerCategory();
    category.setId(CATEGORY_ID);
    category.setParentCategoryId(CATEGORY_ID.replace("3e4f", "9a8b"));
    CedarCategoryId id = CedarCategoryId.build(CATEGORY_ID);
    CategoryServiceSession categorySession = mock(CategoryServiceSession.class);
    when(categorySession.getCategoryById(id)).thenReturn(category);
    CategoryPermissionServiceSession categoryPermissionSession = mock(CategoryPermissionServiceSession.class);
    Set<CategoryCapability> readOnly = Set.of(CategoryCapability.READ_CATEGORY);
    when(categoryPermissionSession.getCategoryAuthority(id)).thenReturn(new CategoryAuthority(CategoryRole.VIEWER, false));
    when(categoryPermissionSession.getCategoryCapabilities(id)).thenReturn(readOnly);

    FolderServerCategoryCurrentUserReport report =
        GraphDbPermissionReader.getCategoryCurrentUserReport(categorySession, categoryPermissionSession, id);

    assertEquals(CATEGORY_ID, report.getId());
    assertEquals(readOnly, report.getCurrentUserPermissions().getCapabilities());
    assertFalse(report.getCurrentUserPermissions().isCanWrite());
    assertFalse(report.isRoot());
    assertTrue(report.getCurrentUserPermissions().isCanRead());
  }
}
