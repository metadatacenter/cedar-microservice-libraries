package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.core.common.bytes.BytesArray;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.util.List;
import java.util.Map;

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
 * What the resource server's deep search answers with, built from what OpenSearch returns. The query
 * the worker sends is tested with the worker. These pin the service's own decisions: every page of a
 * continued walk reports the total its first page counted, each result carries what the searching
 * user may do with it, and a refused continuation reaches the caller as the refusal it is.
 */
class NodeSearchingServiceTest {

  private static final String TEMPLATE_ID = "https://repo.metadatacenter.org/templates/33333333-3333-3333-3333-333333333333";

  private RestHighLevelClient client;
  private NodeSearchingService service;
  private CedarRequestContext context;
  private MockedStatic<CedarDataServices> dataServices;

  @BeforeEach
  void setUp() throws Exception {
    OpensearchConfig opensearch = new ObjectMapper().readValue(
        "{\"indexes\":{\"searchIndex\":{\"name\":\"cedar-search\"}},\"maxResultWindow\":3,\"searchContextKeepAlive\":60000}",
        OpensearchConfig.class);
    CedarConfig config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
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
  void tearDown() {
    dataServices.close();
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
