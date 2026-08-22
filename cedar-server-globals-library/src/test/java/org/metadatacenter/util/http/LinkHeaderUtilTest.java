package org.metadatacenter.util.http;

import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.net.URIBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.constant.HttpConstants;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LinkHeaderUtilTest {

  private static final String BASE = "https://repo.example/resources?q=heart";

  static Stream<Arguments> pagePositions() {
    return Stream.of(
        Arguments.of(0L, 10, 0, null, null),
        Arguments.of(1L, 10, 0, null, null),
        Arguments.of(100L, 10, 0, null, 10L),
        Arguments.of(100L, 10, 10, 0L, 20L),
        Arguments.of(100L, 10, 50, 40L, 60L),
        Arguments.of(100L, 10, 90, 80L, null),
        Arguments.of(95L, 10, 90, 80L, null),
        Arguments.of(95L, 10, 80, 70L, 90L),
        Arguments.of(7L, 3, 3, 0L, 6L)
    );
  }

  @ParameterizedTest
  @MethodSource("pagePositions")
  void exposesPreviousAndNextOnlyWhenThosePagesExist(long total, int limit, int offset,
                                                      Long expectedPrevious, Long expectedNext) throws Exception {
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(BASE, total, limit, offset);

    assertOptionalOffset(links, HttpConstants.HEADER_LINK_TYPE_PREV, expectedPrevious);
    assertOptionalOffset(links, HttpConstants.HEADER_LINK_TYPE_NEXT, expectedNext);
    assertEquals(0L, offsetOf(links.get(HttpConstants.HEADER_LINK_TYPE_FIRST)));
    long expectedLast = total == 0 ? 0 : ((total - 1) / limit) * limit;
    assertEquals(expectedLast, offsetOf(links.get(HttpConstants.HEADER_LINK_TYPE_LAST)));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(ints = {Integer.MIN_VALUE, -1, 0})
  void invalidPageSizeProducesNoBrokenOrDivideByZeroLinks(Integer limit) {
    assertTrue(LinkHeaderUtil.getPagingLinkHeaders(BASE, 100L, limit, 0).isEmpty());
  }

  @ParameterizedTest
  @ValueSource(longs = {Long.MIN_VALUE, -100, -1})
  void invalidTotalProducesNoLinks(long total) {
    assertTrue(LinkHeaderUtil.getPagingLinkHeaders(BASE, total, 10, 0).isEmpty());
  }

  @Test
  void nullOffsetDefaultsToFirstPage() throws Exception {
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(BASE, 100L, 10, null);
    assertFalse(links.containsKey(HttpConstants.HEADER_LINK_TYPE_PREV));
    assertEquals(10L, offsetOf(links.get(HttpConstants.HEADER_LINK_TYPE_NEXT)));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://repo.example/resources?offset=0&limit=10",
      "https://repo.example/resources?limit=10&offset=0&q=heart",
      "https://repo.example/resources?offset=999&offset=0&limit=1&limit=10",
      "https://repo.example/resources?q=heart%20disease&offset=0&limit=10"
  })
  void replacesExistingPaginationParametersInsteadOfDuplicatingThem(String baseUrl) throws Exception {
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(baseUrl, 100L, 10, 0);

    for (String link : links.values()) {
      assertEquals(1, values(link, "offset").size());
      assertEquals(1, values(link, "limit").size());
      assertEquals("10", values(link, "limit").get(0));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"q", "resource_types", "publication_status"})
  void preservesUnrelatedQueryParameters(String parameter) throws Exception {
    String base = "https://repo.example/resources?" + parameter + "=a%20value&offset=5&limit=5";
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(base, 30L, 5, 5);

    for (String link : links.values()) {
      assertEquals(List.of("a value"), values(link, parameter));
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://repo.example/resources", "http://localhost:8080/resources?q=x"})
  void uriOverloadMatchesStringOverload(String base) {
    assertEquals(LinkHeaderUtil.getPagingLinkHeaders(base, 42L, 10, 10),
        LinkHeaderUtil.getPagingLinkHeaders(URI.create(base), 42L, 10, 10));
  }

  @Test
  void combinedHeaderContainsEveryGeneratedRelationInRfcLinkSyntax() {
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(BASE, 100L, 10, 50);
    String header = LinkHeaderUtil.getPagingLinkHeader(BASE, 100L, 10, 50);

    assertEquals(4, links.size());
    for (Map.Entry<String, String> link : links.entrySet()) {
      assertTrue(header.contains("<" + link.getValue() + ">; rel=\"" + link.getKey() + "\""));
    }
  }

  private static void assertOptionalOffset(Map<String, String> links, String relation, Long expected) throws Exception {
    if (expected == null) {
      assertFalse(links.containsKey(relation));
    } else {
      assertEquals(expected.longValue(), offsetOf(links.get(relation)));
    }
  }

  private static long offsetOf(String uri) throws Exception {
    return Long.parseLong(values(uri, "offset").get(0));
  }

  private static List<String> values(String uri, String name) throws Exception {
    return new URIBuilder(uri).getQueryParams().stream()
        .filter(p -> name.equals(p.getName()))
        .map(NameValuePair::getValue)
        .toList();
  }
}
