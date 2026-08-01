package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.lucene.search.TotalHits;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchResponse.Clusters;
import org.opensearch.action.search.SearchResponseSections;
import org.opensearch.action.search.ShardSearchFailure;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
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
        "{\"indexes\":{\"searchIndex\":{\"name\":\"cedar-search\"}}}", OpensearchConfig.class);
    client = mock(RestHighLevelClient.class);
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
      FilesystemResourcePermission permission, String userKey, boolean everybodyRead, boolean everybodyWrite) {
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
  void administrativeReadOverrideRemovesAllAccessClausesButRetainsTypeFilter() {
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

  private SearchResponseResult executeSearch(String query, List<String> resourceTypes, ResourceVersionFilter version,
                                             ResourcePublicationStatusFilter publicationStatus, String categoryId,
                                             List<String> sort, int limit, int offset) throws Exception {
    return worker.search(requestContext, query, resourceTypes, version, publicationStatus, categoryId, sort, limit, offset);
  }

  private String capturedQuery() {
    return capturedRequest.get().source().toString();
  }

  private static SearchResponse emptyResponse(long total) {
    SearchHits hits = new SearchHits(SearchHits.EMPTY,
        new TotalHits(total, TotalHits.Relation.EQUAL_TO), Float.NaN);
    SearchResponseSections sections = new SearchResponseSections(hits, null, null, false, null, null, 1);
    return new SearchResponse(sections, null, 1, 1, 0, 1L, ShardSearchFailure.EMPTY_ARRAY, Clusters.EMPTY);
  }
}
