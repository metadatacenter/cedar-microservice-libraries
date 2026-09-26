package org.metadatacenter.util.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PagedListResponseTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** The smallest listing: the envelope plus a collection under a name of its own. */
  static final class Rows extends PagedListResponse {
    private final List<String> rows;

    Rows(List<String> rows, String requestUrl, long total, int limit, int offset, boolean capped) {
      this.rows = rows;
      page(requestUrl, total, limit, offset, capped);
    }

    public List<String> getRows() {
      return rows;
    }
  }

  @Test
  void aMiddlePageNamesAllFourNeighbours() {
    Rows page = new Rows(List.of("c", "d"), "http://h/x?limit=2&offset=2", 7, 2, 2, false);

    Map<String, String> paging = page.getPaging();
    assertEquals(Map.of("offset", "0", "limit", "2"), query(paging.get("first")));
    assertEquals(Map.of("offset", "0", "limit", "2"), query(paging.get("prev")));
    assertEquals(Map.of("offset", "4", "limit", "2"), query(paging.get("next")));
    assertEquals(Map.of("offset", "6", "limit", "2"), query(paging.get("last")));
    assertEquals(7, page.getTotalCount());
    assertEquals(2, page.getCurrentOffset());
    assertEquals(new PagedListResponse.PageRequest(2, 2), page.getRequest());
  }

  @Test
  void theFirstPageHasNoPreviousAndTheLastHasNoNext() {
    Rows first = new Rows(List.of("a", "b"), "http://h/x", 4, 2, 0, false);
    Rows last = new Rows(List.of("c", "d"), "http://h/x", 4, 2, 2, false);

    assertFalse(first.getPaging().containsKey("prev"));
    assertTrue(first.getPaging().containsKey("next"));
    assertTrue(last.getPaging().containsKey("prev"));
    assertFalse(last.getPaging().containsKey("next"));
  }

  @Test
  void linksKeepTheCallersFiltersAndReplaceOnlyThePagingParameters() {
    Rows page = new Rows(List.of(), "http://h/logs?q=folders&minDurationMs=5&offset=10&limit=10", 100, 10, 10,
        false);

    Map<String, String> next = query(page.getPaging().get("next"));

    assertEquals("folders", next.get("q"));
    assertEquals("5", next.get("minDurationMs"));
    assertEquals("20", next.get("offset"));
    assertEquals("10", next.get("limit"));
  }

  @Test
  void aCappedCountDropsTheLastLinkAndKeepsNext() {
    Rows page = new Rows(List.of("a"), "http://h/x", 10_100, 100, 0, true);

    assertFalse(page.getPaging().containsKey("last"));
    assertTrue(page.getPaging().containsKey("next"));
    assertTrue(page.isCountCapped());
  }

  @Test
  void countCappedIsSerializedOnlyWhenTrue() {
    JsonNode exact = MAPPER.valueToTree(new Rows(List.of("a"), "http://h/x", 1, 10, 0, false));
    JsonNode capped = MAPPER.valueToTree(new Rows(List.of("a"), "http://h/x", 10, 10, 0, true));

    assertFalse(exact.has("countCapped"));
    assertTrue(capped.get("countCapped").asBoolean());
  }

  @Test
  void theEnvelopeSerializesInTheResourceServersShape() {
    JsonNode json = MAPPER.valueToTree(new Rows(List.of("a", "b"), "http://h/x", 2, 10, 0, false));

    assertEquals(2, json.get("totalCount").asLong());
    assertEquals(0, json.get("currentOffset").asLong());
    assertEquals(10, json.get("request").get("limit").asInt());
    assertEquals(0, json.get("request").get("offset").asInt());
    assertTrue(json.get("paging").has("first"));
    assertEquals(2, json.get("rows").size());
  }

  @Test
  void anEmptyListingHasNoNextAndReportsZero() {
    Rows page = new Rows(List.of(), "http://h/x", 0, 10, 0, false);

    assertEquals(0, page.getTotalCount());
    assertFalse(page.getPaging().containsKey("next"));
    assertFalse(page.getPaging().containsKey("prev"));
  }

  private static Map<String, String> query(String url) {
    Map<String, String> out = new java.util.HashMap<>();
    String q = URI.create(url).getRawQuery();
    for (String pair : q.split("&")) {
      String[] kv = pair.split("=", 2);
      out.put(kv[0], kv.length > 1 ? java.net.URLDecoder.decode(kv[1], java.nio.charset.StandardCharsets.UTF_8) : "");
    }
    return out;
  }
}
