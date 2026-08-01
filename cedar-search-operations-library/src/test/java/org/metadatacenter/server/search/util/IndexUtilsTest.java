package org.metadatacenter.server.search.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IndexUtilsTest {

  private IndexUtils indexUtils;
  private ElasticsearchManagementService managementService;

  @BeforeEach
  void setUp() {
    indexUtils = new IndexUtils(mock(CedarConfig.class, RETURNS_DEEP_STUBS));
    managementService = mock(ElasticsearchManagementService.class);
  }

  @ParameterizedTest
  @EnumSource(value = CedarResourceType.class, mode = EnumSource.Mode.EXCLUDE, names = "FOLDER")
  void indexesEveryNonFolderResourceType(CedarResourceType type) {
    FileSystemResource resource = mock(FileSystemResource.class);
    when(resource.getType()).thenReturn(type);

    assertTrue(indexUtils.needsIndexing(resource));
  }

  static Stream<Arguments> folderIndexingStates() {
    return Stream.of(
        Arguments.of(false, false, true),
        Arguments.of(true, false, false),
        Arguments.of(false, true, false),
        Arguments.of(true, true, false));
  }

  @ParameterizedTest
  @MethodSource("folderIndexingStates")
  void excludesSystemAndUserHomeFolders(boolean system, boolean userHome, boolean expected) {
    FolderServerFolder folder = mock(FolderServerFolder.class);
    when(folder.getType()).thenReturn(CedarResourceType.FOLDER);
    when(folder.isSystem()).thenReturn(system);
    when(folder.isUserHome()).thenReturn(userHome);

    assertTrue(indexUtils.needsIndexing(folder) == expected);
  }

  @Test
  void generatedIndexNameUsesAliasAndSortableTimestamp() {
    String generated = indexUtils.getNewIndexName("cedar-search");

    assertTrue(generated.matches("cedar-search-\\d{4}-\\d{2}-\\d{2}t\\d{6}"), generated);
  }

  @Test
  void rolloverDeletesOnlyPriorIndicesBelongingToExactAliasFamily() throws Exception {
    String alias = "cedar-search";
    String newIndex = "cedar-search-2026-07-31t220000";
    when(managementService.getAllIndices()).thenReturn(List.of(
        "cedar-search-2025-01-01t000000",
        newIndex,
        "cedar-search",
        "cedar-search-backup",
        "cedar-search2-2025-01-01t000000",
        "cedar-rules-2025-01-01t000000",
        ".opensearch-system"));

    indexUtils.deleteOldIndices(managementService, alias, newIndex);

    verify(managementService).deleteIndex("cedar-search-2025-01-01t000000");
    verify(managementService).deleteIndex("cedar-search");
    verify(managementService, never()).deleteIndex(newIndex);
    verify(managementService, never()).deleteIndex("cedar-search-backup");
    verify(managementService, never()).deleteIndex("cedar-search2-2025-01-01t000000");
    verify(managementService, never()).deleteIndex("cedar-rules-2025-01-01t000000");
    verify(managementService, never()).deleteIndex(".opensearch-system");
  }

  @ParameterizedTest
  @ValueSource(strings = {"cedar-search", "cedar-search-2026-07-31t220000"})
  void existingExactAliasFamilyPreventsCreation(String existingIndex) throws Exception {
    when(managementService.getAllIndices()).thenReturn(List.of(existingIndex));

    indexUtils.ensureIndexAndAliasExist(managementService, "cedar-search");

    verify(managementService, never()).createSearchIndex(anyString());
    verify(managementService, never()).addAlias(anyString(), anyString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"cedar-search-backup", "cedar-search2-2026-07-31t220000", "cedar-rules-2026-07-31t220000"})
  void similarlyPrefixedForeignIndexDoesNotSuppressRequiredCreation(String foreignIndex) throws Exception {
    when(managementService.getAllIndices()).thenReturn(List.of(foreignIndex));

    indexUtils.ensureIndexAndAliasExist(managementService, "cedar-search");

    verify(managementService).createSearchIndex(anyString());
    verify(managementService).addAlias(anyString(), org.mockito.ArgumentMatchers.eq("cedar-search"));
  }

  @Test
  void emptyClusterCreatesIndexAndAttachesAlias() throws Exception {
    when(managementService.getAllIndices()).thenReturn(List.of());

    indexUtils.ensureIndexAndAliasExist(managementService, "cedar-search");

    verify(managementService).createSearchIndex(anyString());
    verify(managementService).addAlias(anyString(), org.mockito.ArgumentMatchers.eq("cedar-search"));
  }

  @Test
  void allResourceLoaderAccumulatesConcretePagesInRepositoryOrder() throws Exception {
    List<Integer> offsets = new ArrayList<>();

    List<FileSystemResource> resources = indexUtils.findAllResources(offset -> {
      offsets.add(offset);
      return offset == 0
          ? page(3, 0, template("template-1"), template("template-2"))
          : page(3, 2, template("template-3"));
    });

    assertEquals(List.of("template-1", "template-2", "template-3"),
        resources.stream().map(FileSystemResource::getId).toList());
    assertEquals(List.of(0, 2), offsets);
  }

  @Test
  void resourcePageReadFailureEscapesInsteadOfPromotingPartialIndex() {
    assertThrows(CedarProcessingException.class, () -> indexUtils.findAllResources(offset -> {
      if (offset == 0) {
        return page(3, 0, template("template-1"), template("template-2"));
      }
      throw new CedarProcessingException("neo4j offline");
    }));
  }

  @Test
  void emptyPageBeforeReportedTotalEscapesInsteadOfSpinningForever() {
    CedarProcessingException error = assertThrows(CedarProcessingException.class,
        () -> indexUtils.findAllResources(offset -> page(3, offset)));

    assertTrue(error.getMessage().contains("made no progress"));
  }

  private static FolderServerNodeListResponse page(long total, int offset,
                                                   FolderServerResourceExtract... resources) {
    FolderServerNodeListResponse page = new FolderServerNodeListResponse();
    page.setTotalCount(total);
    page.setCurrentOffset(offset);
    page.setResources(List.of(resources));
    return page;
  }

  private static FolderServerTemplateExtract template(String id) {
    FolderServerTemplateExtract template = new FolderServerTemplateExtract();
    template.setId(id);
    template.setName(id);
    return template;
  }
}
