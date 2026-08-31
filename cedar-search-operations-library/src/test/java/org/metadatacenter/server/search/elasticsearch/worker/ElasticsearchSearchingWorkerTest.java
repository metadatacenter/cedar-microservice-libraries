package org.metadatacenter.server.search.elasticsearch.worker;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.mockito.ArgumentCaptor;
import org.opensearch.action.search.ClearScrollRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.search.SearchScrollRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.search.SearchHit;
import org.opensearch.search.SearchHits;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ElasticsearchSearchingWorkerTest {

  private RestHighLevelClient client;
  private ElasticsearchSearchingWorker worker;

  @BeforeEach
  void setUp() {
    OpensearchConfig config = mock(OpensearchConfig.class, RETURNS_DEEP_STUBS);
    when(config.getIndexes().getSearchIndex().getName()).thenReturn("cedar-search");
    when(config.getSearchContextKeepAlive()).thenReturn(60_000);
    when(config.getSize()).thenReturn(100);
    client = mock(RestHighLevelClient.class);
    worker = new ElasticsearchSearchingWorker(config, client);
  }

  static Stream<Arguments> dottedPaths() {
    return Stream.of(
        Arguments.of("id", Map.of("id", "root"), "root"),
        Arguments.of("info.id", Map.of("info", Map.of("id", "one")), "one"),
        Arguments.of("info.owner.id", Map.of("info", Map.of("owner", Map.of("id", "two"))), "two"),
        Arguments.of("a.b.c.id", Map.of("a", Map.of("b", Map.of("c", Map.of("id", "three")))), "three"));
  }

  @ParameterizedTest
  @MethodSource("dottedPaths")
  void extractsValuesAtArbitraryDottedPathDepth(String path, Map<String, Object> source, String expected)
      throws Exception {
    doReturn(response("scroll-1", source)).when(client).search(any(SearchRequest.class), any(RequestOptions.class));
    doReturn(emptyResponse()).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    assertEquals(List.of(expected), worker.findAllValuesForField(path));
  }

  @Test
  void preservesHitAndScrollPageOrder() throws Exception {
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "one")),
        Map.of("info", Map.of("id", "two")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doReturn(response("scroll-2", Map.of("info", Map.of("id", "three"))), emptyResponse()).when(client)
        .scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    assertEquals(List.of("one", "two", "three"), worker.findAllValuesForField("info.id"));
  }

  @Test
  void skipsHitsWithMissingNullContainerOrNonStringValue() throws Exception {
    Map<String, Object> missing = Map.of("other", "value");
    Map<String, Object> nullContainer = new java.util.HashMap<>();
    nullContainer.put("info", null);
    Map<String, Object> nonString = Map.of("info", Map.of("id", 42));
    Map<String, Object> valid = Map.of("info", Map.of("id", "kept"));
    doReturn(response("scroll-1", missing, nullContainer, nonString, valid)).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doReturn(emptyResponse()).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    assertEquals(List.of("kept"), worker.findAllValuesForField("info.id"));
  }

  @Test
  void initialIoFailureEscapesInsteadOfLookingLikeAnEmptyIndex() throws Exception {
    doThrow(new IOException("offline")).when(client).search(any(SearchRequest.class), any(RequestOptions.class));

    CedarDependencyUnavailableException error = assertThrows(CedarDependencyUnavailableException.class,
        () -> worker.findAllValuesForField("info.id"));
    assertEquals("OpenSearch is unavailable", error.getMessage());
  }

  @Test
  void laterScrollIoFailureEscapesInsteadOfReturningAConvincingPartialList() throws Exception {
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "kept")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doThrow(new IOException("offline")).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    CedarDependencyUnavailableException error = assertThrows(CedarDependencyUnavailableException.class,
        () -> worker.findAllValuesForField("info.id"));
    assertEquals("OpenSearch is unavailable", error.getMessage());
  }

  @Test
  void clearsTheScrollContextWhenThePagesRunOut() throws Exception {
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "one")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doReturn(response("scroll-2", Map.of("info", Map.of("id", "two"))), emptyResponse()).when(client)
        .scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    assertEquals(List.of("one", "two"), worker.findAllValuesForField("info.id"));

    // The last page answers with the id the context is held under by then, which is the one to release.
    verifyScrollCleared("scroll-end");
  }

  @Test
  void clearsTheScrollContextWhenAPageFails() throws Exception {
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "one")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doThrow(new IOException("offline")).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    assertThrows(CedarDependencyUnavailableException.class, () -> worker.findAllValuesForField("info.id"));

    verifyScrollCleared("scroll-1");
  }

  @Test
  void opensNoScrollToClearWhenTheFirstRequestFails() throws Exception {
    doThrow(new IOException("offline")).when(client).search(any(SearchRequest.class), any(RequestOptions.class));

    assertThrows(CedarDependencyUnavailableException.class, () -> worker.findAllValuesForField("info.id"));

    verify(client, never()).clearScroll(any(ClearScrollRequest.class), any(RequestOptions.class));
  }

  @Test
  void aFailedClearIsLoggedRatherThanReplacingTheValuesItWasReleasing() throws Exception {
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "one")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doReturn(emptyResponse()).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));
    doThrow(new IOException("offline")).when(client)
        .clearScroll(any(ClearScrollRequest.class), any(RequestOptions.class));

    assertEquals(List.of("one"), worker.findAllValuesForField("info.id"));
  }

  @Test
  void aFailedClearIsLoggedRatherThanReplacingTheFailureItWasReleasing() throws Exception {
    doThrow(new IOException("offline")).when(client)
        .clearScroll(any(ClearScrollRequest.class), any(RequestOptions.class));
    doReturn(response("scroll-1", Map.of("info", Map.of("id", "one")))).when(client)
        .search(any(SearchRequest.class), any(RequestOptions.class));
    doThrow(new IOException("offline")).when(client).scroll(any(SearchScrollRequest.class), any(RequestOptions.class));

    CedarDependencyUnavailableException error = assertThrows(CedarDependencyUnavailableException.class,
        () -> worker.findAllValuesForField("info.id"));
    assertEquals("OpenSearch is unavailable", error.getMessage());
  }

  private void verifyScrollCleared(String expectedScrollId) throws Exception {
    ArgumentCaptor<ClearScrollRequest> clearRequest = ArgumentCaptor.forClass(ClearScrollRequest.class);
    verify(client).clearScroll(clearRequest.capture(), any(RequestOptions.class));
    assertEquals(List.of(expectedScrollId), clearRequest.getValue().getScrollIds());
  }

  @SafeVarargs
  private static SearchResponse response(String scrollId, Map<String, Object>... sources) {
    SearchHit[] hits = new SearchHit[sources.length];
    for (int i = 0; i < sources.length; i++) {
      hits[i] = mock(SearchHit.class);
      when(hits[i].getSourceAsMap()).thenReturn(sources[i]);
    }
    SearchHits searchHits = mock(SearchHits.class);
    when(searchHits.getHits()).thenReturn(hits);
    SearchResponse response = mock(SearchResponse.class);
    when(response.getHits()).thenReturn(searchHits);
    when(response.getScrollId()).thenReturn(scrollId);
    return response;
  }

  private static SearchResponse emptyResponse() {
    return response("scroll-end");
  }
}
