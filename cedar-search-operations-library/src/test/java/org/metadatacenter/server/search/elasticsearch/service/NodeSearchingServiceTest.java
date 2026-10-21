package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.http.HttpHost;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.error.CedarErrorReasonKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.search.elasticsearch.FakeOpenSearch;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.mockito.MockedStatic;
import org.opensearch.OpenSearchStatusException;
import org.opensearch.action.search.CreatePitRequest;
import org.opensearch.action.search.CreatePitResponse;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponse.Clusters;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * What the resource server's searches answer with, built from what OpenSearch returns. The query the
 * worker sends is tested with the worker. These pin the service's own decisions: every page of a
 * continued walk reports the total its first page counted, each result carries what the searching
 * user may do with it, and a refused continuation reaches the caller as the refusal it is.
 *
 * <p>The tests that start an in-process cluster send what the service sends over the wire and read
 * back what a cluster answers, so they pin what a user sees: which results a search returns, in what
 * order a walk delivers them, and which continuations it refuses.</p>
 */
@Tag("integration")
class NodeSearchingServiceTest {

  private static final String TEMPLATE_ID = "https://repo.metadatacenter.org/templates/33333333-3333-3333-3333-333333333333";
  private static final String INDEX = "cedar-search";
  private static final String READER = "user-1|viewer";
  private static final String URL = "https://resource.metadatacenter.orgx/search-deep";

  private CedarConfig config;
  private RestHighLevelClient client;
  private NodeSearchingService service;
  private CedarRequestContext context;
  private MockedStatic<CedarDataServices> dataServices;
  private FakeOpenSearch openSearch;
  private RestHighLevelClient clusterClient;
  private NodeSearchingService clusterService;

  @BeforeEach
  void setUp() throws Exception {
    OpensearchConfig opensearch = new ObjectMapper().readValue(
        "{\"indexes\":{\"searchIndex\":{\"name\":\"cedar-search\"}},\"maxResultWindow\":3,\"searchContextKeepAlive\":60000}",
        OpensearchConfig.class);
    config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(config.getElasticsearchConfig()).thenReturn(opensearch);
    when(config.getTrustedFolders().getFoldersMap()).thenReturn(Map.of());
    client = mock(RestHighLevelClient.class);
    when(client.createPit(any(CreatePitRequest.class), any(RequestOptions.class)))
        .thenReturn(new CreatePitResponse("pit-opened", 0L, 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY));
    service = new NodeSearchingService(config, client);

    CedarUser user = new CedarUser();
    user.setId("user-1");
    context = mock(CedarRequestContext.class);
    when(context.getCedarUser()).thenReturn(user);
    CedarDataServices services = mock(CedarDataServices.class);
    when(services.getCategoryServiceSession(any())).thenReturn(mock(CategoryServiceSession.class));
    dataServices = mockStatic(CedarDataServices.class);
    dataServices.when(CedarDataServices::getInstance).thenReturn(services);
  }

  @AfterEach
  void tearDown() throws Exception {
    dataServices.close();
    if (openSearch != null) {
      clusterClient.close();
      openSearch.close();
    }
  }

  @Test
  void everyPageOfAWalkReportsTheTotalItsFirstPageCounted() throws Exception {
    when(client.search(any(SearchRequest.class), any(RequestOptions.class)))
        .thenReturn(response(40, hit(0, "user-1|viewer"), hit(1, "user-1|viewer")))
        .thenReturn(response(0, hit(2, "user-1|viewer"), hit(3, "user-1|viewer")));

    DeepSearchPageResponse first = page(null, 0, 0, null);
    DeepSearchPageResponse next = page(first.pointInTimeId(), 2, first.response().getTotalCount(),
        first.nextSearchAfter());

    assertEquals(40, first.response().getTotalCount());
    assertEquals(0, first.response().getCurrentOffset());
    assertEquals("pit-opened", first.pointInTimeId());
    assertEquals(40, next.response().getTotalCount(),
        "a continued page counts nothing, so it must report the total the walk began with");
    assertEquals(2, next.response().getCurrentOffset());
    assertEquals("pit-opened", next.pointInTimeId());
  }

  @Test
  void eachResultCarriesWhatTheSearchingUserMayDoWithIt() throws Exception {
    when(client.search(any(SearchRequest.class), any(RequestOptions.class)))
        .thenReturn(response(2, hit(0, "user-1|editor"), hit(1, "user-2|manager")));

    FolderServerNodeListResponse listing = page(null, 0, 0, null).response();

    List<? extends FolderServerResourceExtract> results = listing.getResources();
    assertEquals(2, results.size());
    assertSame(ResourceRole.EDITOR, results.get(0).getCurrentUserPermissions().getRole());
    assertTrue(results.get(0).getCurrentUserPermissions().isCanEdit());
    assertFalse(results.get(0).getCurrentUserPermissions().isCanWrite());
    assertEquals(null, results.get(1).getCurrentUserPermissions().getRole(),
        "another user's grant gives the searching user nothing");
    assertFalse(results.get(1).getCurrentUserPermissions().isCanRead());
    assertEquals("kidney", listing.getRequest().getQ());
  }

  /**
   * A continuation whose snapshot has been released is answered with a 400 that tells the caller to
   * start again. Wrapping it as a processing failure would turn that into a 500 that says nothing.
   */
  @Test
  void anExpiredContinuationReachesTheCallerAsItsOwnRefusal() throws Exception {
    when(client.search(any(SearchRequest.class), any(RequestOptions.class)))
        .thenThrow(new OpenSearchStatusException("No search context found", RestStatus.NOT_FOUND));

    CedarException refused = assertThrows(CedarException.class,
        () -> page("pit-released", 2, 40, new Object[]{1.0f, "cid-1"}));

    assertSame(CedarErrorReasonKey.CONTINUATION_EXPIRED, refused.getErrorPack().getErrorReasonKey());
    assertSame(CedarResponseStatus.BAD_REQUEST, refused.getErrorPack().getStatus());
  }

  /**
   * A walk reads one snapshot of the index, so it delivers every match once, in the order asked for,
   * under one total, even when a match is written while the walk is under way. The snapshot is released
   * when the last page comes back short.
   */
  @Test
  void aDeepWalkDeliversEveryMatchOnceInOrderUnderOneTotal() throws Exception {
    startCluster();
    for (String name : List.of("Kidney G", "Kidney C", "Kidney E", "Kidney A", "Kidney F", "Kidney B", "Kidney D")) {
      index(name, READER, null);
    }
    index("Kidney Secret", "user-2|manager", null);
    index("Liver A", READER, null);

    List<String> delivered = new ArrayList<>();
    List<Long> totals = new ArrayList<>();
    DeepSearchPageResponse page = clusterPage(null, 0, 0, null);
    index("Kidney D2", READER, null);
    while (true) {
      delivered.addAll(names(page.response()));
      totals.add(page.response().getTotalCount());
      if (!page.hasMore()) {
        break;
      }
      page = clusterPage(page.pointInTimeId(), delivered.size(), page.response().getTotalCount(),
          page.nextSearchAfter());
    }

    assertEquals(List.of("Kidney A", "Kidney B", "Kidney C", "Kidney D", "Kidney E", "Kidney F", "Kidney G"), delivered);
    assertEquals(List.of(7L, 7L, 7L), totals);
    assertEquals(0, openSearch.openPointsInTime(), "a finished walk releases its snapshot");
  }

  /** An offset deep search reaches its page by skipping through one snapshot, which it releases afterwards. */
  @Test
  void anOffsetDeepSearchAnswersThePageAtItsOffsetWithTheWholeTotal() throws Exception {
    startCluster();
    for (String name : List.of("Kidney G", "Kidney C", "Kidney E", "Kidney A", "Kidney F", "Kidney B", "Kidney D")) {
      index(name, READER, null);
    }

    FolderServerNodeListResponse listing = clusterService.searchDeep(context, "kidney", null, List.of("template"),
        ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, null, List.of("name"), 2, 4, URL);

    assertEquals(List.of("Kidney E", "Kidney F"), names(listing));
    assertEquals(7, listing.getTotalCount());
    assertEquals(0, openSearch.openPointsInTime());
  }

  /** The permission clause is part of the query, so a result the searcher may not read is neither shown nor counted. */
  @Test
  void everySearchHidesAndLeavesUncountedWhatTheSearcherMayNotRead() throws Exception {
    startCluster();
    index("Kidney Granted", READER, null);
    index("Kidney Public", null, "read");
    index("Kidney Private", "user-2|manager", null);

    List<FolderServerNodeListResponse> listings = List.of(
        clusterService.search(context, "kidney", null, List.of("template"), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, null, 10, 0, URL),
        clusterService.searchDeep(context, "kidney", null, List.of("template"), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, null, 10, 0, URL),
        clusterPage(null, 0, 0, null).response());

    for (FolderServerNodeListResponse listing : listings) {
      assertEquals(Set.of("Kidney Granted", "Kidney Public"), Set.copyOf(names(listing)));
      assertEquals(2, listing.getTotalCount());
    }
  }

  /**
   * A search reads the Boolean grammar and the quotes a user writes, and every other character as
   * literal text. A slash would otherwise open a regular expression in the cluster's query_string
   * grammar, and malformed grammar is the caller's error rather than the server's.
   */
  @Test
  void searchTextIsReadAsCedarGrammarAndLiteralTextRatherThanAsClusterSyntax() throws Exception {
    startCluster();
    index("Kidney Study", READER, null);
    index("Liver Panel", READER, null);
    index("Left Kidney", READER, null);

    assertEquals(Set.of("Kidney Study", "Liver Panel", "Left Kidney"), searchNames("kidney OR liver"));
    assertEquals(Set.of("Kidney Study"), searchNames("kidney AND study"));
    assertEquals(Set.of("Kidney Study"), searchNames("kidney NOT left"));
    assertEquals(Set.of("Kidney Study", "Left Kidney"), searchNames("left kidney"));
    assertEquals(Set.of("Left Kidney"), searchNames("\"left kidney\""));
    assertEquals(Set.of("Kidney Study", "Left Kidney"), searchNames("kid*"));
    assertEquals(Set.of(), searchNames("kidney/left"));

    CedarException malformed = assertThrows(CedarException.class, () -> searchNames("kidney AND ("));
    assertSame(CedarResponseStatus.BAD_REQUEST, malformed.getErrorPack().getStatus());
  }

  /** A continuation whose snapshot the cluster has released, by its keep alive or a restart, says to start again. */
  @Test
  void aContinuationWhoseSnapshotIsGoneIsABadRequestThatSaysToStartAgain() throws Exception {
    startCluster();
    for (String name : List.of("Kidney A", "Kidney B", "Kidney C")) {
      index(name, READER, null);
    }
    DeepSearchPageResponse first = clusterPage(null, 0, 0, null);

    openSearch.expirePointsInTime();
    CedarException expired = assertThrows(CedarException.class, () -> clusterPage(first.pointInTimeId(), 2,
        first.response().getTotalCount(), first.nextSearchAfter()));

    assertSame(CedarErrorReasonKey.CONTINUATION_EXPIRED, expired.getErrorPack().getErrorReasonKey());
    assertSame(CedarResponseStatus.BAD_REQUEST, expired.getErrorPack().getStatus());
  }

  /**
   * A continuation is not signed, so a caller can hand back a snapshot or a position no walk issued. The
   * cluster refuses to read either, and that refusal is the caller's 400, not a server fault.
   */
  @Test
  void aContinuationTheClusterCannotReadIsABadRequest() throws Exception {
    startCluster();
    for (String name : List.of("Kidney A", "Kidney B", "Kidney C")) {
      index(name, READER, null);
    }
    DeepSearchPageResponse first = clusterPage(null, 0, 0, null);

    CedarException forgedSnapshot = assertThrows(CedarException.class, () -> clusterPage("not-a-snapshot", 2,
        first.response().getTotalCount(), first.nextSearchAfter()));
    CedarException forgedPosition = assertThrows(CedarException.class, () -> clusterPage(first.pointInTimeId(), 2,
        first.response().getTotalCount(), new Object[]{"Kidney B"}));

    assertSame(CedarResponseStatus.BAD_REQUEST, forgedSnapshot.getErrorPack().getStatus());
    assertSame(CedarResponseStatus.BAD_REQUEST, forgedPosition.getErrorPack().getStatus());
  }

  private void startCluster() throws Exception {
    openSearch = new FakeOpenSearch();
    clusterClient = new RestHighLevelClient(RestClient.builder(new HttpHost("127.0.0.1", openSearch.port(), "http")));
    clusterService = new NodeSearchingService(config, clusterClient);
  }

  /** Indexes a template under its name, granted to one principal or shared with everybody. */
  private void index(String name, String grant, String everybodyPermission) throws Exception {
    String cid = "cid-" + name.toLowerCase(Locale.ROOT).replace(' ', '-');
    ObjectNode document = new ObjectMapper().createObjectNode().put("cid", cid);
    document.putObject("info").put("@id", TEMPLATE_ID + "-" + cid).put("resourceType", "template")
        .put("schema:name", name).put("ownedBy", "https://metadatacenter.org/users/owner");
    ArrayNode users = document.putArray("users");
    if (grant != null) {
      users.add(grant);
    }
    if (everybodyPermission != null) {
      document.put("computedEverybodyPermission", everybodyPermission);
    }
    new ElasticsearchIndexingWorker(INDEX, clusterClient).addToIndex(document, cid);
  }

  /** A page of a walk sorted by name, three results at a time, through the in-process cluster. */
  private DeepSearchPageResponse clusterPage(String pointInTimeId, long rowsSeen, long knownTotal, Object[] searchAfter)
      throws CedarException {
    return clusterService.searchDeepPage(context, "kidney", null, List.of("template"), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, List.of("name"), 3, rowsSeen, knownTotal, pointInTimeId,
        searchAfter, URL);
  }

  private Set<String> searchNames(String query) throws CedarException {
    return Set.copyOf(names(clusterService.search(context, query, null, List.of("template"), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, null, 10, 0, URL)));
  }

  private static List<String> names(FolderServerNodeListResponse listing) {
    return listing.getResources().stream().map(FolderServerResourceExtract::getName).collect(Collectors.toList());
  }

  private DeepSearchPageResponse page(String pointInTimeId, long rowsSeen, long knownTotal, Object[] searchAfter)
      throws CedarException {
    return service.searchDeepPage(context, "kidney", null, List.of("template"), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, null, 2, rowsSeen, knownTotal, pointInTimeId, searchAfter,
        "https://resource.metadatacenter.orgx/search-deep");
  }

  private static SearchHit hit(int docId, String grant) {
    SearchHit hit = new SearchHit(docId);
    hit.sourceRef(new BytesArray("""
        {"cid": "cid-%1$d",
         "info": {"@id": "%2$s-%1$d", "resourceType": "template", "schema:name": "Kidney %1$d",
                  "ownedBy": "https://metadatacenter.org/users/owner"},
         "users": ["%3$s"]}
        """.formatted(docId, TEMPLATE_ID, grant)));
    hit.sortValues(new Object[]{1.0f, "cid-" + docId}, new DocValueFormat[]{DocValueFormat.RAW, DocValueFormat.RAW});
    return hit;
  }

  private static SearchResponse response(long total, SearchHit... searchHits) {
    SearchHits hits = new SearchHits(searchHits, new TotalHits(total, TotalHits.Relation.EQUAL_TO), Float.NaN);
    SearchResponseSections sections = new SearchResponseSections(hits, null, null, false, null, null, 1);
    return new SearchResponse(sections, null, 1, 1, 0, 1L, ShardSearchFailure.EMPTY_ARRAY, Clusters.EMPTY);
  }
}
