package org.metadatacenter.util.http;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CedarURIBuilderTest {
  @ParameterizedTest
  @ValueSource(strings = {"\"C%2B%2B\"", "\"Rad%22\"", "\"Rad%2522\"", "\"{Rad}\"",
      "\"{cedarQuery0}\"", "C++ & café", "rad\\*", "100%", "a#b?c=d"})
  void decodedQueryValuesSurviveRepeatedPaginationLiterally(String expression) {
    UriInfo info = mock(UriInfo.class);
    when(info.getAbsolutePathBuilder()).thenReturn(UriBuilder.fromUri("https://resource.example/search"));
    URI uri = new CedarURIBuilder(info).queryParam("q", Optional.of(expression))
        .queryParam("limit", Optional.of(1)).queryParam("omitted", Optional.empty()).build();
    assertEquals(expression, queryValue(uri, "q"));
    for (String link : LinkHeaderUtil.getPagingLinkHeaders(uri, 3L, 1, 0).values()) {
      assertEquals(expression, queryValue(URI.create(link), "q"));
      for (String again : LinkHeaderUtil.getPagingLinkHeaders(link, 3L, 1, 1).values())
        assertEquals(expression, queryValue(URI.create(again), "q"));
    }
  }

  private static String queryValue(URI uri, String key) {
    return Arrays.stream(uri.getRawQuery().split("&")).map(p -> p.split("=", 2))
        .filter(p -> p[0].equals(key)).map(p -> URLDecoder.decode(p[1], StandardCharsets.UTF_8))
        .findFirst().orElseThrow();
  }
}
