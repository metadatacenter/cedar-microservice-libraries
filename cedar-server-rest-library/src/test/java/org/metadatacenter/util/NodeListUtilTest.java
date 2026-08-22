package org.metadatacenter.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.TrustedFoldersConfig;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.extract.FolderServerFieldExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.util.http.PagedSortedTypedQuery;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;

class NodeListUtilTest {

  private CedarConfig cedarConfig;
  private TrustedFoldersConfig trustedFolders;
  private FolderServiceSession folderSession;
  private PagedSortedTypedQuery query;
  private CedarFolderId folderId;

  @BeforeEach
  void setUp() {
    cedarConfig = mock(CedarConfig.class);
    trustedFolders = mock(TrustedFoldersConfig.class);
    folderSession = mock(FolderServiceSession.class);
    query = mock(PagedSortedTypedQuery.class);
    folderId = CedarFolderId.build("folder-current");
    when(cedarConfig.getTrustedFolders()).thenReturn(trustedFolders);
    when(trustedFolders.getFoldersMap()).thenReturn(Map.of());
    stubQuery(List.of(CedarResourceType.TEMPLATE), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, 10, 0, List.of("name"));
  }

  static Stream<Arguments> requestMappings() {
    return Stream.of(
        Arguments.of(List.of(CedarResourceType.FOLDER), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, 1, 0, List.of("name")),
        Arguments.of(List.of(CedarResourceType.FIELD, CedarResourceType.ELEMENT), ResourceVersionFilter.LATEST,
            ResourcePublicationStatusFilter.DRAFT, 25, 50, List.of("-createdOnTS")),
        Arguments.of(List.of(CedarResourceType.TEMPLATE), ResourceVersionFilter.LATEST_BY_STATUS,
            ResourcePublicationStatusFilter.PUBLISHED, 100, 1000, List.of("name", "-lastUpdatedOnTS")),
        Arguments.of(List.of(CedarResourceType.INSTANCE), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.PUBLISHED, 7, 3, List.of()),
        Arguments.of(List.of(CedarResourceType.FOLDER, CedarResourceType.FIELD, CedarResourceType.ELEMENT,
                CedarResourceType.TEMPLATE, CedarResourceType.INSTANCE), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, 50, 0, List.of("lastUpdatedOnTS"))
    );
  }

  @ParameterizedTest
  @MethodSource("requestMappings")
  void mapsEveryValidatedQueryDimensionIntoWorkspaceRequest(List<CedarResourceType> types,
                                                            ResourceVersionFilter version,
                                                            ResourcePublicationStatusFilter publication,
                                                            int limit, int offset, List<String> sort) {
    stubQuery(types, version, publication, limit, offset, sort);

    NodeListRequest request = NodeListUtil.buildNodeListRequest(query);

    assertSame(types, request.getResourceTypes());
    assertSame(version, request.getVersion());
    assertSame(publication, request.getPublicationStatus());
    assertEquals(limit, request.getLimit());
    assertEquals(offset, request.getOffset());
    assertSame(sort, request.getSort());
  }

  @Test
  void folderListingUsesTheSameRequestForFetchAndCountAndReturnsServiceData() {
    FolderServerFolderExtract parent = folder("folder-current", false);
    List<FolderServerResourceExtract> path = List.of(folder("root", false), parent);
    List<FolderServerResourceExtract> resources = List.of(template("template-1"), field("field-1"));
    when(folderSession.findFolderContentsExtract(any(), any())).thenReturn(resources);
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(37L);

    FolderServerNodeListResponse response = NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId,
        "https://repo.example/folders/contents?limit=10&offset=0", path, query);

    assertEquals(NodeListQueryType.FOLDER_CONTENT, response.getNodeListQueryType());
    assertSame(resources, response.getResources());
    assertSame(path, response.getPathInfo());
    assertEquals(37L, response.getTotalCount());
    assertEquals(0L, response.getCurrentOffset());
    verify(folderSession).findFolderContentsExtract(folderId, response.getRequest());
    verify(folderSession).findFolderContentsCount(folderId, response.getRequest());
  }

  @ParameterizedTest
  @MethodSource("implicitOpenStates")
  void projectsParentImplicitOpennessOntoEveryReturnedResource(Boolean parentState, boolean expected) {
    FolderServerFolderExtract parent = folder("folder-current", parentState);
    FolderServerTemplateExtract template = template("template-1");
    FolderServerFieldExtract field = field("field-1");
    FolderServerFolderExtract childFolder = folder("child", null);
    List<FolderServerResourceExtract> resources = List.of(template, field, childFolder);
    when(folderSession.findFolderContentsExtract(any(), any())).thenReturn(resources);
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(3L);

    NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId,
        "https://repo.example/resources", List.of(parent), query);

    assertEquals(List.of(expected, expected, expected),
        resources.stream().map(FolderServerResourceExtract::getIsOpenImplicitly).toList());
  }

  static Stream<Arguments> implicitOpenStates() {
    return Stream.of(Arguments.of(null, false), Arguments.of(false, false), Arguments.of(true, true));
  }

  @Test
  void trustedParentDecoratesArtifactsButNotFolderChildren() {
    when(trustedFolders.getFoldersMap()).thenReturn(Map.of("folder-current", "Stanford"));
    FolderServerTemplateExtract template = template("template-1");
    FolderServerFieldExtract field = field("field-1");
    FolderServerFolderExtract folder = folder("child", false);
    when(folderSession.findFolderContentsExtract(any(), any()))
        .thenReturn(List.of(template, field, folder));
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(3L);

    NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId, "https://repo.example/resources",
        List.of(folder("folder-current", false)), query);

    assertEquals("Stanford", template.getTrustedBy());
    assertEquals("Stanford", field.getTrustedBy());
  }

  @Test
  void emptyPathIsSafeAndDefaultsReturnedResourcesToNotImplicitlyOpen() {
    FolderServerTemplateExtract template = template("template-1");
    when(folderSession.findFolderContentsExtract(any(), any())).thenReturn(List.of(template));
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(1L);

    FolderServerNodeListResponse response = NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId,
        "https://repo.example/resources", List.of(), query);

    assertFalse(template.getIsOpenImplicitly());
    assertTrue(response.getPathInfo().isEmpty());
  }

  @Test
  void nullPathIsSafeAndPreservedInResponse() {
    FolderServerTemplateExtract template = template("template-1");
    when(folderSession.findFolderContentsExtract(any(), any())).thenReturn(List.of(template));
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(1L);

    FolderServerNodeListResponse response = NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId,
        "https://repo.example/resources", null, query);

    assertFalse(template.getIsOpenImplicitly());
    assertNull(response.getPathInfo());
  }

  static Stream<Arguments> pagingScenarios() {
    return Stream.of(
        Arguments.of(0L, 10, 0, false, false),
        Arguments.of(1L, 10, 0, false, false),
        Arguments.of(100L, 10, 0, true, false),
        Arguments.of(100L, 10, 50, true, true),
        Arguments.of(100L, 10, 90, false, true));
  }

  @ParameterizedTest
  @MethodSource("pagingScenarios")
  void responsePagingMatchesCountLimitAndOffset(long total, int limit, int offset,
                                                 boolean hasNext, boolean hasPrevious) {
    stubQuery(List.of(CedarResourceType.TEMPLATE), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, limit, offset, List.of("name"));
    when(folderSession.findFolderContentsExtract(any(), any())).thenReturn(List.of());
    when(folderSession.findFolderContentsCount(any(), any())).thenReturn(total);

    FolderServerNodeListResponse response = NodeListUtil.findFolderContents(cedarConfig, folderSession, folderId,
        "https://repo.example/resources?limit=" + limit + "&offset=" + offset,
        List.of(folder("folder-current", false)), query);

    assertEquals(hasNext, response.getPaging().containsKey("next"));
    assertEquals(hasPrevious, response.getPaging().containsKey("prev"));
    assertEquals(offset, response.getCurrentOffset());
  }

  private void stubQuery(List<CedarResourceType> types, ResourceVersionFilter version,
                         ResourcePublicationStatusFilter publication, int limit, int offset, List<String> sort) {
    when(query.getResourceTypeList()).thenReturn(types);
    when(query.getVersion()).thenReturn(version);
    when(query.getPublicationStatus()).thenReturn(publication);
    when(query.getLimit()).thenReturn(limit);
    when(query.getOffset()).thenReturn(offset);
    when(query.getSortList()).thenReturn(sort);
  }

  private static FolderServerFolderExtract folder(String id, Boolean implicitlyOpen) {
    FolderServerFolderExtract folder = new FolderServerFolderExtract();
    folder.setId(id);
    folder.setIsOpenImplicitly(implicitlyOpen);
    return folder;
  }

  private static FolderServerTemplateExtract template(String id) {
    FolderServerTemplateExtract template = new FolderServerTemplateExtract(); template.setId(id); return template;
  }

  private static FolderServerFieldExtract field(String id) {
    FolderServerFieldExtract field = new FolderServerFieldExtract(); field.setId(id); return field;
  }
}
