package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.opensearch.action.search.CreatePitRequest;
import org.opensearch.action.search.CreatePitResponse;
import org.opensearch.action.search.DeletePitRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponse.Clusters;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.DocValueFormat;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;
import org.opensearch.search.builder.SearchSourceBuilder;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchPermissionEnabledContentSearchingWorkerTest {

  private RestHighLevelClient client;
  private ElasticsearchPermissionEnabledContentSearchingWorker worker;
  private CedarUser user;
  private CedarRequestContext requestContext;
  private AtomicReference<SearchRequest> capturedRequest;

  @BeforeEach
  void setUp() throws Exception {
    OpensearchConfig config = new ObjectMapper().readValue(
        "{\"indexes\":{\"searchIndex\":{\"name\":\"cedar-search\"}},\"maxResultWindow\":3,\"searchContextKeepAlive\":60000}",
        OpensearchConfig.class);
    client = mock(RestHighLevelClient.class);
    when(client.createPit(any(CreatePitRequest.class), any(RequestOptions.class)))
        .thenReturn(new CreatePitResponse("pit-1", 0L, 1, 1, 0, 0, ShardSearchFailure.EMPTY_ARRAY));
    worker = new ElasticsearchPermissionEnabledContentSearchingWorker(config, client);
    user = new CedarUser();
    user.setId("user-1");
    requestContext = mock(CedarRequestContext.class);
    when(requestContext.getCedarUser()).thenReturn(user);
    capturedRequest = new AtomicReference<>();
    doAnswer(invocation -> {
      capturedRequest.set(invocation.getArgument(0));
      return emptyResponse(7);
    }).when(client).search(any(SearchRequest.class), any(RequestOptions.class));
  }

  static Stream<Arguments> accessiblePermissionQueries() {
    return Stream.of(
        Arguments.of(FilesystemResourcePermission.READ, "user-1|read", true, true),
        Arguments.of(FilesystemResourcePermission.WRITE, "user-1|write", false, true),
        Arguments.of(FilesystemResourcePermission.CHANGEOWNER, "user-1|changeowner", false, false));
  }

  @ParameterizedTest
  @MethodSource("accessiblePermissionQueries")
  void accessibleCountMapsRequestedPermissionWithoutInflatingWriteAccess(
      FilesystemResourcePermission permission, String userKey, boolean everybodyRead,
      boolean everybodyWrite) throws Exception {
    long count = worker.searchAccessibleResourceCountByUser(List.of("template", "instance"), permission, user);

    String query = capturedQuery();
    assertEquals(7, count);
    assertTrue(query.contains(userKey), query);
    assertEquals(everybodyRead, query.contains("\"computedEverybodyPermission\":[\"read\"]"), query);
    assertEquals(everybodyWrite, query.contains("\"computedEverybodyPermission\":[\"write\"]"), query);
    assertTrue(query.contains("template"), query);
    assertTrue(query.contains("instance"), query);
  }

  @Test
  void administrativeReadOverrideRemovesAllAccessClausesButRetainsTypeFilter() throws Exception {
    user.setPermissions(List.of(CedarPermission.READ_NOT_READABLE_NODE.getPermissionName()));

    worker.searchAccessibleResourceCountByUser(List.of("field"), FilesystemResourcePermission.READ, user);

    String query = capturedQuery();
    assertFalse(query.contains("users"), query);
    assertFalse(query.contains("computedEverybodyPermission"), query);
    assertTrue(query.contains("field"), query);
  }

  static Stream<Arguments> versionFilters() {
    return Stream.of(
        Arguments.of(ResourceVersionFilter.LATEST, "info.isLatestVersion", false),
        Arguments.of(ResourceVersionFilter.LATEST_BY_STATUS, "info.isLatestPublishedVersion", true),
        Arguments.of(ResourceVersionFilter.ALL, null, false),
        Arguments.of(null, null, false));
  }

  @ParameterizedTest
  @MethodSource("versionFilters")
  void versionFilterBuildsLatestOrLatestByStatusFallbackSemantics(ResourceVersionFilter version,
                                                                   String expectedField,
                                                                   boolean expectsDraftField) throws Exception {
    executeSearch("", null, version, ResourcePublicationStatusFilter.ALL, null, null, 20, 4);

    String query = capturedQuery();
    if (expectedField == null) {
      assertFalse(query.contains("isLatest"), query);
    } else {
      assertTrue(query.contains(expectedField), query);
      assertTrue(query.contains("exists"), query);
    }
    assertEquals(expectsDraftField, query.contains("info.isLatestDraftVersion"), query);
  }

  static Stream<Arguments> publicationFilters() {
    return Stream.of(
        Arguments.of(ResourcePublicationStatusFilter.DRAFT, "bibo:draft"),
        Arguments.of(ResourcePublicationStatusFilter.PUBLISHED, "bibo:published"),
        Arguments.of(ResourcePublicationStatusFilter.ALL, null),
        Arguments.of(null, null));
  }

  @ParameterizedTest
  @MethodSource("publicationFilters")
  void publicationFilterIncludesLegacyMissingStatusFallback(ResourcePublicationStatusFilter status,
                                                              String expectedValue) throws Exception {
    executeSearch("", null, ResourceVersionFilter.ALL, status, null, null, 20, 4);

    String query = capturedQuery();
    if (expectedValue == null) {
      assertFalse(query.contains("info.bibo:status"), query);
    } else {
      assertTrue(query.contains(expectedValue), query);
      assertTrue(query.contains("exists"), query);
      assertTrue(query.contains("must_not"), query);
    }
  }

  @Test
  void searchCombinesPermissionTypeCategoryPaginationAndTotalHitTracking() throws Exception {
    SearchResponseResult result = executeSearch("", List.of("template", "element"), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, "category-1", null, 25, 75);

    SearchRequest request = capturedRequest.get();
    String query = request.source().toString();
    assertEquals(7, result.getTotalCount());
    assertEquals(75, request.source().from());
    assertEquals(25, request.source().size());
    assertTrue(request.source().trackTotalHitsUpTo() > 0);
    assertTrue(query.contains("user-1|read"), query);
    assertTrue(query.contains("template"), query);
    assertTrue(query.contains("element"), query);
    assertTrue(query.contains("category-1"), query);
  }

  @Test
  void recognizedSortsMapToIndexFieldsAndUnknownSortIsIgnored() throws Exception {
    executeSearch("", null, ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, null,
        List.of("name", "-lastUpdatedOnTS", "createdOnTS", "unknown"), 10, 0);

    String source = capturedRequest.get().source().toString();
    assertTrue(source.contains("info.schema:name"), source);
    assertTrue(source.contains("info.pav:lastUpdatedOn"), source);
    assertTrue(source.contains("\"order\":\"desc\""), source);
    assertTrue(source.contains("info.pav:createdOn"), source);
    assertFalse(source.contains("unknown"), source);
  }

  static Stream<Arguments> rewrittenQueries() {
    return Stream.of(
        Arguments.of("kidney", List.of("info.schema:name.raw", "summaryText.raw")),
        Arguments.of("disease:cancer", List.of("nested", "infoFields.fieldName", "infoFields.fieldValue")),
        Arguments.of("*:cancer", List.of("nested", "infoFields.fieldValue")),
        Arguments.of("disease:*", List.of("nested", "infoFields.fieldName")),
        Arguments.of("disease:https://example.org/concept/A", List.of("infoFields.fieldValueUri", "https%3A%2F%2F")),
        Arguments.of("[pv]female", List.of("possibleValues.valueLabels", "possibleValues.valueConcepts")),
        Arguments.of("[pv]=female", List.of("possibleValues.valueLabels.keyword", "possibleValues.valueConcepts")),
        Arguments.of("[PV]=Female", List.of("possibleValues.valueLabels.keyword", "Female")),
        Arguments.of("kidney AND liver", List.of("must", "kidney", "liver")),
        Arguments.of("kidney NOT liver", List.of("must_not", "kidney", "liver")),
        Arguments.of("disease:\"colorectal cancer\"", List.of("nested", "colorectal cancer")),
        Arguments.of("disease:\"stage: II\"", List.of("nested", "stage: II")),
        Arguments.of("\"study title\":\"colorectal cancer\"", List.of("study title", "colorectal cancer")),
        Arguments.of("(disease:*)", List.of("nested", "infoFields.fieldName")),
        Arguments.of("*:*", List.of("nested", "query_string", "*")),
        Arguments.of("disease:influ*", List.of("infoFields.fieldValue", "influ*")),
        Arguments.of("disease:colo?", List.of("infoFields.fieldValue", "colo?")),
        Arguments.of("disease:https://example.org/A?x=1#part", List.of("infoFields.fieldValueUri", "x%3D1%23part")));
  }

  @ParameterizedTest
  @MethodSource("rewrittenQueries")
  void rewritesUserSyntaxIntoConcreteIndexQueries(String input, List<String> expectedFragments) throws Exception {
    executeSearch(input, null, ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, null, null, 10, 0);

    String query = capturedQuery();
    for (String expected : expectedFragments) {
      assertTrue(query.contains(expected), () -> "Missing '" + expected + "' in " + query);
    }
  }

  @Test
  void malformedLuceneSyntaxReturnsProcessingErrorWithoutCallingSearchBackend() {
    org.metadatacenter.exception.CedarProcessingException error = org.junit.jupiter.api.Assertions.assertThrows(
        org.metadatacenter.exception.CedarProcessingException.class,
        () -> executeSearch("disease:\"unterminated", null, ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, null, 10, 0));

    assertTrue(error.getMessage().contains("Error processing query"), error.getMessage());
    assertEquals(null, capturedRequest.get());
  }

  @Test
  void transportFailureIsUnavailableAndNeverAnEmptyResult() throws Exception {
    doThrow(new IOException("connection refused"))
        .when(client).search(any(SearchRequest.class), any(RequestOptions.class));

    assertThrows(CedarDependencyUnavailableException.class,
        () -> executeSearch("kidney", null, ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, null, 10, 0));
    assertThrows(CedarDependencyUnavailableException.class,
        () -> worker.searchAccessibleResourceCountByUser(
            List.of("template"), FilesystemResourcePermission.READ, user));
  }

  @Test
  void deepSearchWalksTheOffsetWithSearchAfterAndFetchesOnlyTheRequestedPage() throws Exception {
    // The configured window is 3, so an offset of 5 is skipped as 3 rows then 2, before the page of 2 asked for.
    List<Page> pages = deepPages(response(7, hit(0), hit(1), hit(2)), response(7, hit(3), hit(4)),
        response(7, hit(5), hit(6)));

    SearchResponseResult result = worker.searchDeep(requestContext, "kidney", null, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, null, 2, 5);

    assertEquals(7, result.getTotalCount());
    assertEquals(List.of(5, 6), result.getHits().stream().map(SearchHit::docId).toList());
    assertEquals(List.of(3, 2, 2), pages.stream().map(Page::size).toList());
    assertEquals(List.of(false, false, true), pages.stream().map(Page::fetchesDocuments).toList());
    assertEquals(null, pages.get(0).searchAfter());
    assertEquals("cid-2", pages.get(1).searchAfter()[1]);
    assertEquals("cid-4", pages.get(2).searchAfter()[1]);
    // The point in time carries its own indices; naming them again is rejected by OpenSearch.
    assertEquals(0, pages.get(0).indices().length);
    assertTrue(pages.get(0).source().contains("pit-1"), pages.get(0).source());
    verifyPointInTimeDeleted();
  }

  @Test
  void deepSearchAsksForOnePageWhenThereIsNoOffsetToWalk() throws Exception {
    List<Page> pages = deepPages(response(7, hit(0), hit(1)));

    SearchResponseResult result = worker.searchDeep(requestContext, "kidney", null, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, null, 2, 0);

    assertEquals(List.of(0, 1), result.getHits().stream().map(SearchHit::docId).toList());
    assertEquals(1, pages.size());
    assertTrue(pages.get(0).fetchesDocuments());
    verifyPointInTimeDeleted();
  }

  @Test
  void deepSearchReturnsAnEmptyPageWithTheTotalWhenTheOffsetIsPastTheLastRow() throws Exception {
    List<Page> pages = deepPages(response(2, hit(0), hit(1)));

    SearchResponseResult result = worker.searchDeep(requestContext, "kidney", null, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, null, 2, 100);

    assertEquals(2, result.getTotalCount());
    assertTrue(result.getHits().isEmpty());
    assertEquals(1, pages.size());
    verifyPointInTimeDeleted();
  }

  static Stream<Arguments> deepSortOrderings() {
    return Stream.of(
        Arguments.of(null, List.of("_score", "cid")),
        Arguments.of(List.of("name"), List.of("info.schema:name", "cid")),
        Arguments.of(List.of("-lastUpdatedOnTS"), List.of("info.pav:lastUpdatedOn", "cid")));
  }

  @ParameterizedTest
  @MethodSource("deepSortOrderings")
  void deepSearchAppendsTheIdTiebreakerSoSearchAfterOrdersTheHitsTotally(List<String> sortList,
                                                                        List<String> expectedOrder) throws Exception {
    List<Page> pages = deepPages(response(1, hit(0)));

    worker.searchDeep(requestContext, "kidney", null, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, sortList, 2, 0);

    String source = pages.get(0).source();
    int previous = -1;
    for (String field : expectedOrder) {
      int at = source.indexOf("\"" + field + "\"", previous + 1);
      assertTrue(at > previous, () -> "Expected " + expectedOrder + " in sort order, got " + source);
      previous = at;
    }
  }

  @Test
  void deepSearchDeletesItsPointInTimeWhenAPageFails() throws Exception {
    doThrow(new IOException("connection refused"))
        .when(client).search(any(SearchRequest.class), any(RequestOptions.class));

    assertThrows(CedarDependencyUnavailableException.class,
        () -> worker.searchDeep(requestContext, "kidney", null, ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, null, 2, 5));

    verifyPointInTimeDeleted();
  }

  private void verifyPointInTimeDeleted() throws Exception {
    ArgumentCaptor<DeletePitRequest> deleteRequest = ArgumentCaptor.forClass(DeletePitRequest.class);
    verify(client).deletePit(deleteRequest.capture(), any(RequestOptions.class));
    assertEquals(List.of("pit-1"), deleteRequest.getValue().getPitIds());
  }

  /**
   * Answers each search with the next given response, recording what the request asked for at the moment
   * it was made. The worker reuses one source builder across the walk, so only a snapshot is evidence.
   */
  private List<Page> deepPages(SearchResponse... responses) throws IOException {
    List<Page> seen = new ArrayList<>();
    Deque<SearchResponse> remaining = new ArrayDeque<>(List.of(responses));
    doAnswer(invocation -> {
      SearchRequest request = invocation.getArgument(0);
      SearchSourceBuilder source = request.source();
      seen.add(new Page(request.indices(), source.size(),
          source.fetchSource() == null || source.fetchSource().fetchSource(),
          source.searchAfter(), source.toString()));
      return remaining.poll();
    }).when(client).search(any(SearchRequest.class), any(RequestOptions.class));
    return seen;
  }

  private record Page(String[] indices, int size, boolean fetchesDocuments, Object[] searchAfter, String source) {
  }

  private SearchResponseResult executeSearch(String query, List<String> resourceTypes, ResourceVersionFilter version,
                                             ResourcePublicationStatusFilter publicationStatus, String categoryId,
                                             List<String> sort, int limit, int offset) throws Exception {
    return worker.search(requestContext, query, resourceTypes, version, publicationStatus, categoryId, sort, limit, offset);
  }

  private String capturedQuery() {
    return capturedRequest.get().source().toString();
  }

  private static SearchResponse emptyResponse(long total) {
    return response(total);
  }

  private static SearchHit hit(int docId) {
    SearchHit hit = new SearchHit(docId);
    hit.sortValues(new Object[]{1.0f, "cid-" + docId},
        new DocValueFormat[]{DocValueFormat.RAW, DocValueFormat.RAW});
    return hit;
  }



  private static SearchResponse response(long total, SearchHit... searchHits) {
    SearchHits hits = new SearchHits(searchHits,
        new TotalHits(total, TotalHits.Relation.EQUAL_TO), Float.NaN);
    SearchResponseSections sections = new SearchResponseSections(hits, null, null, false, null, null, 1);
    return new SearchResponse(sections, null, 1, 1, 0, 1L, ShardSearchFailure.EMPTY_ARRAY, Clusters.EMPTY);
  }
}
