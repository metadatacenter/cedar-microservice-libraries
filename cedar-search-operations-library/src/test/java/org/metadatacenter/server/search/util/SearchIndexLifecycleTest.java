package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.elasticsearch.FakeOpenSearch;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchServiceFactory;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The search index lives under an alias. A rebuild fills a new concrete index, verifies it holds what it should, moves
 * the alias to it in one step and deletes the indices the alias left; a server that finds no index creates one with
 * its alias. Documents are written under their CEDAR identifier, updated in place when only their permissions change,
 * and removed together with any copies an older writer left under generated identifiers. Every call goes through the
 * real OpenSearch client to an in-process cluster, and every refusal or outage is reported to the caller.
 */
@Tag("integration")
class SearchIndexLifecycleTest {

  private static final String T1 = "https://repo.metadatacenter.orgx/templates/t-1";
  private static final String T2 = "https://repo.metadatacenter.orgx/templates/t-2";
  private static final String T3 = "https://repo.metadatacenter.orgx/templates/t-3";
  private static final String F1 = "https://repo.metadatacenter.orgx/folders/f-1";
  private static final String F2 = "https://repo.metadatacenter.orgx/folders/f-2";
  private static final String GROUP = "https://metadatacenter.org/groups/g-1";
  private static final String VIEWER_GRANT = CedarNodeMaterializedPermissions.getKey(GROUP, ResourceRole.VIEWER);

  private FakeOpenSearch openSearch;
  private CedarConfig config;
  private ElasticsearchManagementService management;
  /** The test's own client, for writing documents the services then read. */
  private RestHighLevelClient client;
  private String alias;

  @BeforeEach
  void startTheCluster() throws Exception {
    openSearch = new FakeOpenSearch();
    Map<String, String> environment = new HashMap<>(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    environment.put("CEDAR_OPENSEARCH_HOST", "127.0.0.1");
    environment.put("CEDAR_OPENSEARCH_REST_PORT", String.valueOf(openSearch.port()));
    config = CedarConfig.buildForEnvironment(environment);
    management = new ElasticsearchManagementService(config.getElasticsearchConfig(), config);
    client = new RestHighLevelClient(RestClient.builder(new HttpHost("127.0.0.1", openSearch.port(), "http")));
    alias = config.getElasticsearchConfig().getIndexes().getSearchIndex().getName();
    IndexRebuildRegistry.reset();
  }

  @AfterEach
  void stopTheCluster() throws Exception {
    client.close();
    management.closeClient();
    openSearch.close();
    IndexRebuildRegistry.reset();
  }

  @Test
  void aSearchIndexIsCreatedOnceWithTheShippedSettingsAndMappings() throws Exception {
    management.verifyConnectivity();
    management.createSearchIndex("cedar-search-a");

    FakeOpenSearch.Index created = openSearch.index("cedar-search-a");
    assertTrue(created.mappings.path("properties").has("cid"), "the mapping names the CEDAR id: " + created.mappings);
    assertFalse(created.settings.isEmpty(), "the shipped settings are sent");
    assertTrue(management.indexExists("cedar-search-a"));
    assertFalse(management.indexExists("absent"));
    assertThrows(CedarProcessingException.class, () -> management.createSearchIndex("cedar-search-a"));
  }

  @Test
  void aRebuiltIndexIsPromotedOnlyWhenItHoldsWhatItShould() throws Exception {
    IndexUtils utils = new IndexUtils(config);
    String old = alias + "-2026-01-01t000000";
    String fresh = alias + "-2026-02-01t000000";
    management.createSearchIndex(old);
    management.addAlias(old, alias);
    management.createSearchIndex("unrelated");
    management.createSearchIndex(fresh);
    ElasticsearchIndexingWorker worker = new ElasticsearchIndexingWorker(fresh, client);
    worker.addToIndex(document(T1), T1);
    worker.addToIndex(document(T2), T2);

    CedarProcessingException refused = assertThrows(CedarProcessingException.class,
        () -> utils.verifyAndPromoteIndex(management, alias, fresh, 3));
    assertTrue(refused.getMessage().contains("expected 3 documents but found 2"), refused.getMessage());
    assertEquals(Set.of(alias), openSearch.index(old).aliases, "a refused index leaves the alias where it was");

    utils.verifyAndPromoteIndex(management, alias, fresh, 2);
    assertEquals(Set.of(alias), openSearch.index(fresh).aliases);
    assertEquals(Set.of(fresh, "unrelated"), openSearch.indexNames(),
        "the index the alias left is deleted, and an index outside the alias is left alone");
  }

  @Test
  void aServerThatFindsNoSearchIndexCreatesOneWithItsAlias() throws Exception {
    new RegenerateSearchIndexTask(config).ensureSearchIndexExists();

    Set<String> names = openSearch.indexNames();
    assertEquals(1, names.size());
    String created = names.iterator().next();
    assertTrue(created.matches(alias + "-\\d{4}-\\d{2}-\\d{2}t\\d{6}"), created);
    assertEquals(Set.of(alias), openSearch.index(created).aliases);

    new IndexUtils(config).ensureIndexAndAliasExist(ElasticsearchServiceFactory.getInstance(config)
        .getManagementService(), alias);
    assertEquals(names, openSearch.indexNames(), "a present index is left alone");
  }

  @Test
  void documentsAreWrittenPatchedAndRemovedWithTheirLegacyCopies() throws Exception {
    management.createSearchIndex("cedar-search-a");
    ElasticsearchIndexingWorker worker = new ElasticsearchIndexingWorker("cedar-search-a", client);

    assertEquals(T1, worker.addToIndex(document(T1), T1).getId());
    worker.addBatch(List.of(new IndexingDocumentDocument(T2), new IndexingDocumentDocument(T3)));
    worker.addBatch(List.of());
    // An older writer indexed this copy under a generated identifier.
    worker.addToIndex(document(T2));
    worker.refreshIndex();
    management.refreshIndex("cedar-search-a");
    assertEquals(4, management.countDocuments("cedar-search-a"));

    ObjectNode patch = JsonMapper.STRICT_MAPPER.createObjectNode();
    patch.putArray("groups").add("g-1|viewer");
    assertTrue(worker.updatePermissionProjection(patch, T1));
    assertEquals("g-1|viewer", openSearch.index("cedar-search-a").documents.get(T1).path("groups").get(0).asText());
    assertFalse(worker.updatePermissionProjection(patch, T1 + "-missing"), "a missing document must be rebuilt whole");
    assertEquals(Set.of(T1 + "-missing"), worker.updatePermissionProjections(Map.of(T2, patch, T1 + "-missing", patch)));
    assertEquals(Set.of(), worker.updatePermissionProjections(Map.of()));

    assertEquals(2, worker.removeAllFromIndex(CedarTemplateId.build(T2)), "the document and its legacy copy");
    assertEquals(0, worker.removeAllFromIndex(CedarTemplateId.build(T2)));
    assertEquals(1, worker.removeAllFromIndex("cid", T3));
    assertEquals(0, worker.removeAllFromIndex("cid", T3));
    worker.removeFromIndex(T1);
    assertThrows(CedarProcessingException.class, () -> worker.removeFromIndex(T1), "removing what is absent fails");
    assertEquals(0, management.countDocuments("cedar-search-a"));
  }

  @Test
  void aDocumentIsFoundByItsIdentifierAndTheIndexIsReadWhole() throws Exception {
    management.createSearchIndex(alias + "-2026-01-01t000000");
    management.addAlias(alias + "-2026-01-01t000000", alias);
    ElasticsearchIndexingWorker worker = new ElasticsearchIndexingWorker(alias, client);
    ObjectNode shared = document(T1);
    shared.putArray("groups").add(CedarGroupId.build("https://metadatacenter.org/groups/g-1").getId() + "|read");
    worker.addToIndex(shared, T1);
    for (int i = 2; i <= 25; i++) {
      worker.addToIndex(document(T1 + "-" + i), T1 + "-" + i);
    }
    NodeSearchingService searching = ElasticsearchServiceFactory.getInstance(config).nodeSearchingService();

    assertEquals(T1, searching.getDocumentByCedarId(CedarTemplateId.build(T1)).get("cid"));
    assertNull(searching.getDocumentByCedarId(CedarTemplateId.build(T1 + "-absent")));
    assertEquals(T1, searching.getByCedarId(T1).getId());
    assertNull(searching.getByCedarId(T1 + "-absent"));
    assertEquals(25, searching.findAllValuesForField("cid").size(), "the scroll reads every page");
    assertEquals(List.of(T1), searching.findAllCedarIdsForGroup(CedarGroupId.build("https://metadatacenter.org/groups/g-1")));
    assertTrue(openSearch.requests.contains("DELETE /_search/scroll"), "the scroll is released");
  }

  @Test
  void aRemovalDuringARebuildIsMirroredIntoTheIndexBeingFilled() throws Exception {
    String live = alias + "-2026-01-01t000000";
    String rebuilding = alias + "-2026-02-01t000000";
    management.createSearchIndex(live);
    management.addAlias(live, alias);
    management.createSearchIndex(rebuilding);
    new ElasticsearchIndexingWorker(live, client).addToIndex(document(T1), T1);
    new ElasticsearchIndexingWorker(rebuilding, client).addToIndex(document(T1), T1);
    NodeIndexingService indexing = ElasticsearchServiceFactory.getInstance(config).nodeIndexingService();

    IndexRebuildRegistry.begin(rebuilding);
    assertEquals(1, indexing.removeDocumentFromIndex(CedarTemplateId.build(T1)));
    assertTrue(IndexRebuildRegistry.liveTouched().contains(T1), "the rebuild learns not to write it back");
    IndexRebuildRegistry.end(rebuilding);

    assertEquals(Map.of(), openSearch.index(live).documents);
    assertEquals(Map.of(), openSearch.index(rebuilding).documents);
  }

  /**
   * A permission change made while a rebuild runs has to reach the index being filled too, or the
   * promoted index carries the grants from before the change. Each index is patched where it holds the
   * resource and given the whole document where it does not, and the rebuild is told to leave all of
   * them alone.
   */
  @Test
  void aPermissionBatchDuringARebuildReachesTheIndexBeingFilled() throws Exception {
    String live = alias + "-2026-01-01t000000";
    String rebuilding = alias + "-2026-02-01t000000";
    management.createSearchIndex(live);
    management.addAlias(live, alias);
    management.createSearchIndex(rebuilding);
    ElasticsearchIndexingWorker liveWorker = new ElasticsearchIndexingWorker(live, client);
    liveWorker.addToIndex(withContent(T1), T1);
    liveWorker.addToIndex(withContent(T2), T2);
    new ElasticsearchIndexingWorker(rebuilding, client).addToIndex(withContent(T1), T1);
    NodeIndexingService indexing = spy(ElasticsearchServiceFactory.getInstance(config).nodeIndexingService());
    doAnswer(call -> whole(call.getArgument(0), call.getArgument(1)))
        .when(indexing).createIndexDocument(any(), any(), any(), any(), anyBoolean());

    IndexRebuildRegistry.begin(rebuilding);
    indexing.updatePermissionProjections(List.of(granted(T1), granted(T2), granted(T3)));
    assertEquals(Set.of(T1, T2, T3), IndexRebuildRegistry.liveTouched(), "the rebuild must write none of them back");
    IndexRebuildRegistry.end(rebuilding);

    for (String index : List.of(live, rebuilding)) {
      Map<String, ObjectNode> documents = openSearch.index(index).documents;
      assertEquals(Set.of(T1, T2, T3), documents.keySet(), index);
      for (String id : List.of(T1, T2, T3)) {
        assertEquals(VIEWER_GRANT, documents.get(id).path("groups").get(0).asText(), index + " " + id);
      }
    }
    assertTrue(openSearch.index(live).documents.get(T2).has("infoFields"), "a patch keeps the indexed content");
    assertTrue(openSearch.index(rebuilding).documents.get(T1).has("infoFields"), "a patch keeps the indexed content");
    assertEquals("whole", openSearch.index(live).documents.get(T3).path("summaryText").asText());
    assertEquals("whole", openSearch.index(rebuilding).documents.get(T2).path("summaryText").asText(),
        "an index without the resource gets all of it, not a bare patch");
    assertEquals("whole", openSearch.index(rebuilding).documents.get(T3).path("summaryText").asText());
  }

  /**
   * Without force, a rebuild compares the identifiers the graph holds with those the index holds, and
   * replaces the index only when they differ.
   */
  @Test
  void anUnforcedRebuildReplacesTheIndexOnlyWhenItDiffersFromTheGraph() throws Exception {
    String current = alias + "-2026-01-01t000000";
    management.createSearchIndex(current);
    management.addAlias(current, alias);
    ElasticsearchIndexingWorker worker = new ElasticsearchIndexingWorker(current, client);
    worker.addToIndex(document(F1), F1);
    worker.addToIndex(document(F2), F2);
    CedarDataServices services = dataServices(List.of(folder(F1, "First"), folder(F2, "Second")));

    try (var data = mockStatic(CedarDataServices.class)) {
      data.when(CedarDataServices::getInstance).thenReturn(services);
      new RegenerateSearchIndexTask(config).regenerateSearchIndex(false, null);
      assertEquals(Set.of(current), openSearch.indexNames(), "an index that matches the graph is kept");

      openSearch.index(current).documents.remove(F2);
      new RegenerateSearchIndexTask(config).regenerateSearchIndex(false, null);
    }

    Set<String> names = openSearch.indexNames();
    assertEquals(1, names.size());
    String promoted = names.iterator().next();
    assertNotEquals(current, promoted, "an index missing a resource is replaced");
    assertEquals(Set.of(alias), openSearch.index(promoted).aliases);
    assertEquals(Set.of(F1, F2), openSearch.index(promoted).documents.keySet());
  }

  /**
   * A rebuild reads its work list before it writes, so a resource saved after that has a newer document
   * than the rebuild's copy. The save is mirrored into the new index, and the rebuild leaves that
   * resource alone rather than overwriting it with the version it started with.
   */
  @Test
  void aRebuildPromotesEveryResourceAndKeepsASaveMadeWhileItRan() throws Exception {
    String old = alias + "-2026-01-01t000000";
    management.createSearchIndex(old);
    management.addAlias(old, alias);
    CedarDataServices services = dataServices(List.of(folder(F1, "First"), folder(F2, "Second")));
    IndexingProgress progress = spy(new IndexingProgress());
    // The save lands after the rebuild has created its index and before it writes anything there.
    doAnswer(call -> {
      ElasticsearchServiceFactory.getInstance(config).nodeIndexingService()
          .indexDocument(folder(F2, "Second, renamed"), grants(F2), null, null);
      return call.callRealMethod();
    }).when(progress).setTotalByType(any());

    try (var data = mockStatic(CedarDataServices.class)) {
      data.when(CedarDataServices::getInstance).thenReturn(services);
      new RegenerateSearchIndexTask(config).regenerateSearchIndex(true, null, progress);
    }

    Set<String> names = openSearch.indexNames();
    assertEquals(1, names.size(), "the old index is deleted once the new one is promoted");
    String promoted = names.iterator().next();
    assertNotEquals(old, promoted);
    assertEquals(Set.of(alias), openSearch.index(promoted).aliases);
    Map<String, ObjectNode> documents = openSearch.index(promoted).documents;
    assertEquals(Set.of(F1, F2), documents.keySet());
    assertEquals("First", documents.get(F1).path("summaryText").asText());
    assertEquals("Second, renamed", documents.get(F2).path("summaryText").asText(),
        "the rebuild's older copy must not replace the save");
    assertEquals(IndexingPhase.DONE, progress.getPhase());
    assertTrue(IndexRebuildRegistry.inProgressIndex().isEmpty(), "mirroring stops when the rebuild ends");
  }

  @Test
  void refusalsAndAnUnreachableClusterAreReported() throws Exception {
    management.createSearchIndex("cedar-search-a");
    openSearch.acknowledge = false;
    assertThrows(CedarProcessingException.class, () -> management.createSearchIndex("cedar-search-b"));
    assertThrows(CedarProcessingException.class, () -> management.addAlias("cedar-search-a", alias));
    assertThrows(CedarProcessingException.class, () -> management.replaceAlias("cedar-search-a", alias));
    assertThrows(CedarProcessingException.class, () -> management.deleteAlias("cedar-search-a", alias));
    assertThrows(CedarProcessingException.class, () -> management.deleteIndex("cedar-search-a"));
    openSearch.acknowledge = true;
    // An unacknowledged change may still have been applied, as here, where the index was created regardless.
    assertTrue(management.deleteIndex("cedar-search-b"));
    assertThrows(CedarProcessingException.class, () -> management.deleteIndex("cedar-search-b"),
        "deleting an index that is not there is reported");

    openSearch.close();
    assertThrows(CedarProcessingException.class, management::verifyConnectivity);
    assertFalse(management.indexExists("cedar-search-a"), "an unreachable cluster reads as no index");
    assertEquals(List.of(), management.getAllIndices());
    assertThrows(CedarProcessingException.class, () -> management.refreshIndex("cedar-search-a"));
    assertThrows(CedarProcessingException.class, () -> management.countDocuments("cedar-search-a"));
    assertThrows(CedarProcessingException.class, () -> management.createSearchIndex("cedar-search-c"));
    assertThrows(CedarProcessingException.class, () -> management.addAlias("cedar-search-a", alias));
    assertThrows(CedarProcessingException.class, () -> management.deleteAlias("cedar-search-a", alias));
  }

  private static ObjectNode document(String cid) {
    ObjectNode document = JsonMapper.STRICT_MAPPER.createObjectNode().put("cid", cid);
    document.putObject("info").put("name", cid.substring(cid.lastIndexOf('/') + 1));
    return document;
  }

  /** A document as a full indexing left it, with extracted content that no permission change may touch. */
  private static ObjectNode withContent(String cid) {
    ObjectNode document = document(cid);
    document.putArray("infoFields").addObject().put("fieldName", "Study title");
    document.putArray("groups");
    return document;
  }

  /** The whole document a stubbed extraction builds, carrying the permissions it was given. */
  private static IndexingDocumentDocument whole(FileSystemResource resource,
                                                CedarNodeMaterializedPermissions permissions) {
    IndexingDocumentDocument document = new IndexingDocumentDocument(resource.getId());
    document.setSummaryText("whole");
    document.setMaterializedPermissions(permissions);
    return document;
  }

  private static NodeIndexingService.PermissionProjection granted(String id) {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(id);
    ObjectNode patch = JsonMapper.STRICT_MAPPER.createObjectNode();
    patch.putArray("groups").add(VIEWER_GRANT);
    return new NodeIndexingService.PermissionProjection(template, grants(id), null, null, patch);
  }

  private static CedarNodeMaterializedPermissions grants(String id) {
    CedarNodeMaterializedPermissions permissions =
        new CedarNodeMaterializedPermissions(CedarTemplateId.build(id), NodeSharePermission.NONE);
    permissions.setGroupRole(GROUP, ResourceRole.VIEWER);
    return permissions;
  }

  private static FolderServerFolder folder(String id, String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setId(id);
    folder.setName(name);
    return folder;
  }

  /** The graph a rebuild reads: these resources on one page, each shared with the test group. */
  private static CedarDataServices dataServices(List<FileSystemResource> graph) {
    FolderServiceSession folders = mock(FolderServiceSession.class);
    List<FolderServerResourceExtract> page = graph.stream().map(FolderServerResourceExtract::fromNode).toList();
    when(folders.findAllNodes(anyInt(), anyInt(), any())).thenReturn(page);
    when(folders.findAllNodesCount()).thenReturn((long) page.size());
    ResourcePermissionServiceSession permissions = mock(ResourcePermissionServiceSession.class);
    when(permissions.getResourceMaterializedPermission(any(FileSystemResource.class)))
        .thenAnswer(call -> grants(call.<FileSystemResource>getArgument(0).getId()));
    CedarDataServices services = mock(CedarDataServices.class);
    when(services.getFolderServiceSession(any())).thenReturn(folders);
    when(services.getResourcePermissionServiceSession(any())).thenReturn(permissions);
    when(services.getCategoryServiceSession(any())).thenReturn(mock(CategoryServiceSession.class));
    return services;
  }
}
