package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.search.elasticsearch.FakeOpenSearch;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchServiceFactory;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
