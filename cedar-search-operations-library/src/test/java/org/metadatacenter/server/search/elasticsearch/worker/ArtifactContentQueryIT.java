package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.model.request.ModifiedDateRange;
import org.metadatacenter.model.request.SearchSharingFilter;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.search.SearchHit;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Real matching with the shipped mapping, crossed with grouping, caller permissions and endpoint.
 * The REST corpus additionally proves extraction from stored artifacts and graph authorization. */
@Tag("integration")
class ArtifactContentQueryIT {
  private static final String INDEX = "cedar-content-query-it-" + UUID.randomUUID();
  private static final String URI = "https://example.org/terms/A?x=1&y=two#part";
  private static RestHighLevelClient client;
  private static ElasticsearchPermissionEnabledContentSearchingWorker worker;

  @BeforeAll
  static void seed() throws Exception {
    client = new RestHighLevelClient(RestClient.builder(new HttpHost(
        System.getenv().getOrDefault("CEDAR_OPENSEARCH_HOST", "127.0.0.1"),
        Integer.parseInt(System.getenv().getOrDefault("CEDAR_OPENSEARCH_REST_PORT", "9200")), "http")));
    ObjectMapper mapper = new ObjectMapper();
    try (var input = ArtifactContentQueryIT.class.getResourceAsStream("/cedar-search.json")) {
      var definition = mapper.readTree(input);
      client.indices().create(new CreateIndexRequest(INDEX)
          .settings(definition.get("settings").toString(), XContentType.JSON)
          .mapping(definition.at("/mappings/_doc").toString(), XContentType.JSON), RequestOptions.DEFAULT);
    }
    worker = new ElasticsearchPermissionEnabledContentSearchingWorker(mapper.readValue("""
        {"indexes":{"searchIndex":{"name":"%s"}},"maxResultWindow":100,"searchContextKeepAlive":60000}
        """.formatted(INDEX), OpensearchConfig.class), client);
    var ny = List.of(field("title", "title", "Statistics"),
        field("publisher", "publishing institution", "City of New York"),
        field("Contact Email", "Contact Email", "research@example.org"),
        Map.of("fieldName", "topic", "fieldValue", "Statistics", "fieldValueUri", encode(URI)));
    index("ny", ny, false);
    index("private-twin", ny, true);
    index("math", List.of(field("title", "title", "Mathematics"),
        field("publisher", "publishing institution", "City of New York")), false);
    index("boston", List.of(field("title", "title", "Statistics"),
        field("publisher", "publishing institution", "City of Boston")), false);
    index("gap", List.of(field("publisher", "publishing institution", "City of beautiful New York")), false);
    index("reversed", List.of(field("publisher", "publishing institution", "York New of City")), false);
    index("swapped", List.of(field("title", "title", "City of New York"),
        field("publisher", "publishing institution", "Statistics")), false);
    index("punctuation", List.of(field("Notes", "Notes", "stage: II")), false);
    index("email-distractor", List.of(field("Contact Email", "Contact Email", "nested@example.org"),
        field("publisher", "publishing institution", "Nested Institute")), false);
    index("date", List.of(field("SampleDate", "SampleDate", "2024-02-29")), false);
    index("different-uri", List.of(Map.of("fieldName", "topic", "fieldValue", "Statistics",
        "fieldValueUri", encode("https://example.org/terms/B"))), false);
    index("parenthesized-uri", List.of(Map.of("fieldName", "topic", "fieldValueUri",
        encode("https://example.org/terms/A_(B)"))), false);
    index("empty", List.of(), false);
    index("title-only", List.of(), false, "Astronomy register", "corpus Astronomy register");
    index("description-only", List.of(), false, "Record metadata", "corpus Astronomy register");
    index("value-only", List.of(field("Notes", "Notes", "Astronomy register")), false);
    index("rad-word", List.of(field("Notes", "Notes", "Rad dose")), false, "Rad dose", "corpus Rad dose");
    index("rad-substring", List.of(field("Notes", "Notes", "Radiation grade")), false, "Radiation grade", "corpus Radiation grade");
    index("slash", List.of(field("Units", "Units", "Moles/volume")), false,
        "Moles/volume in Dried blood spot", "corpus Moles/volume in Dried blood spot");
    index("hyphen", List.of(), false, "2-Dimensional Electrophoresis", "corpus 2-Dimensional Electrophoresis");
    index("tie-z", List.of(), false, "Paging tie", "Paging tie");
    index("tie-a", List.of(), false, "Paging tie", "Paging tie");
    index("pv-word", List.of(), false);
    index("pv-substring", List.of(), false);
    client.update(new org.opensearch.action.update.UpdateRequest(INDEX, "pv-word").doc(Map.of(
        "possibleValues", Map.of("valueLabels", List.of("Rad", "Yes", "Not available")))), RequestOptions.DEFAULT);
    client.update(new org.opensearch.action.update.UpdateRequest(INDEX, "pv-substring").doc(Map.of(
        "possibleValues", Map.of("valueLabels", List.of("Radiation", "Yes indeed", "Not always available")))), RequestOptions.DEFAULT);
    indexSharing("owner-private", "owner", List.of(key("owner")), null);
    indexSharing("reader-owned", "reader", List.of(key("reader")), null);
    for (ResourceRole role : List.of(ResourceRole.VIEWER, ResourceRole.EDITOR, ResourceRole.MANAGER))
      indexSharing("shared-" + role.name(), "owner", List.of(key("owner"),
          CedarNodeMaterializedPermissions.getKey("reader", role)), null);
    indexSharing("public-read", "owner", List.of(key("owner")), "read");
    indexSharing("public-write", "owner", List.of(key("owner")), "write");
    indexSharing("inaccessible", "third", List.of(key("third")), null);
    client.indices().refresh(new RefreshRequest(INDEX), RequestOptions.DEFAULT);
  }

  private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
  private static Map<String, String> field(String name, String label, String value) {
    return Map.of("fieldName", name, "fieldPrefLabel", label, "fieldValue", value);
  }
  private static void index(String id, List<? extends Map<String, String>> fields, boolean hidden) throws Exception {
    index(id, fields, hidden, id, "corpus " + id);
  }
  private static void index(String id, List<? extends Map<String, String>> fields, boolean hidden,
                            String name, String summary) throws Exception {
    client.index(new IndexRequest(INDEX).id(id).source(Map.of(
        "cid", id, "summaryText", summary, "infoFields", fields,
        "info", Map.of("resourceType", "instance", "schema:name", name),
        "users", hidden ? List.of(key("owner")) : List.of(key("owner"), key("reader")))), RequestOptions.DEFAULT);
  }
  private static String key(String user) { return CedarNodeMaterializedPermissions.getKey(user, ResourceRole.VIEWER); }
  private static void indexSharing(String id, String owner, List<String> users, String everybody) throws Exception {
    var source = new LinkedHashMap<String, Object>(Map.of("cid", id, "summaryText", "sharingprobe",
        "infoFields", List.of(field("Notes", "Notes", "Sharingprobe")), "users", users,
        "info", Map.of("resourceType", "instance", "schema:name", id, "ownedBy", owner)));
    if (everybody != null) source.put("computedEverybodyPermission", everybody);
    client.index(new IndexRequest(INDEX).id(id).source(source), RequestOptions.DEFAULT);
  }
  private static CedarRequestContext context(String id) {
    CedarUser user = new CedarUser(); user.setId(id);
    CedarRequestContext context = mock(CedarRequestContext.class);
    when(context.getCedarUser()).thenReturn(user);
    return context;
  }
  private record Case(String query, Set<String> expected) {}
  private static Case c(String query, String... ids) { return new Case(query, Set.of(ids)); }

  static Stream<Arguments> matrix() {
    List<Case> cases = List.of(
        c("publisher:\"City of New York\"", "ny", "private-twin", "math"),
        c("\"publishing institution\":\"City of New York\"", "ny", "private-twin", "math"),
        c("\"Contact Email\":\"research@example.org\"", "ny", "private-twin"),
        c("title:Statistics AND publisher:\"City of New York\"", "ny", "private-twin"),
        c("title:Mathematics OR (title:Statistics AND publisher:\"City of New York\")", "ny", "private-twin", "math"),
        c("title:Mathematics OR title:Statistics AND publisher:\"City of New York\"", "ny", "private-twin", "math"),
        c("title:Statistics NOT publisher:\"City of New York\"", "boston"),
        c("publisher:", "ny", "private-twin", "math", "boston", "gap", "reversed", "swapped", "email-distractor"),
        c("publisher:*", "ny", "private-twin", "math", "boston", "gap", "reversed", "swapped", "email-distractor"),
        c("\"publishing institution\":", "ny", "private-twin", "math", "boston", "gap", "reversed", "swapped", "email-distractor"),
        c("*:\"City of New York\"", "ny", "private-twin", "math", "swapped"),
        c(":\"City of New York\"", "ny", "private-twin", "math", "swapped"),
        c("Publish*:\"City of New York\"", "ny", "private-twin", "math"),
        c("publ?sher:\"City of New York\"", "ny", "private-twin", "math"),
        c("Notes:\"stage: II\"", "punctuation"),
        c("Notes:\"stage: III\""),
        c("SampleDate:\"2024-02-29\"", "date"),
        c("SampleDate:\"2024-02-28\""),
        c("publisher:\"nested@example.org\""),
        c("topic:" + URI, "ny", "private-twin"),
        c("topic:https://example.org/terms/B", "different-uri"),
        c("topic:https://example.org/terms/A_(B)", "parenthesized-uri"),
        c("publisher:" + URI),
        c("(topic:" + URI + ") AND title:Statistics", "ny", "private-twin"),
        c("Astronomy", "title-only", "description-only"),
        c("stronom", "title-only", "description-only"),
        c("Astron*", "title-only", "description-only"),
        c("Astron?my", "title-only", "description-only"),
        c("\"Astronomy register\"", "title-only", "description-only"),
        c("Notes:Astronomy", "value-only"),
        c("Astronomy AND Notes:Astronomy"),
        c("Astronomy OR Notes:Astronomy", "title-only", "description-only", "value-only"),
        c("Rad", "rad-word", "rad-substring"),
        c("\"Rad\"", "rad-word"),
        c("\"RAD\"", "rad-word"),
        c("(\"Rad\" OR Astronomy) AND NOT grade", "rad-word", "title-only", "description-only"),
        c("rad*", "rad-word", "rad-substring"),
        c("rad\\*"),
        c("r\\?d"),
        c("\"rad*\"", "rad-word"),
        c("\"2-Dimensional\"", "hyphen"),
        c("\"2-Dimensional Electrophoresis\"", "hyphen"),
        c("\"Moles/volume\"", "slash"),
        c("\"Moles/volume in Dried\"", "slash"),
        c("\"Moles volume in Dried\"", "slash"),
        c("Units:\"Moles/volume\"", "slash"),
        c("Notes:\"Rad\"", "rad-word"),
        c("Notes:Rad\\*", "rad-word"),
        c("Notes: Rad", "rad-word"),
        c("Notes: AND \"Rad\"", "rad-word"),
        c("[pv]Rad", "pv-word"),
        c("[pv]rad*", "pv-word", "pv-substring"),
        c("[pv]\"rad*\"", "pv-word"),
        c("[pv]=YES", "pv-word"),
        c("[pv]=\"Yes\"", "pv-word"),
        c("[pv]\"Not available\"", "pv-word"),
        c("[pv]=\"Not available\"", "pv-word"),
        c("[pv]Yes AND NOT [pv]=\"Yes indeed\"", "pv-word"));
    return cases.stream().flatMap(test -> Stream.of(false, true).flatMap(wrapped ->
        Stream.of("owner", "reader").flatMap(user -> Stream.of(false, true).map(deep -> Arguments.of(
            wrapped ? "corpus AND (" + test.query + ")" : test.query, user, deep,
            test.expected.stream().filter(id -> user.equals("owner") || !id.equals("private-twin")).collect(Collectors.toSet()))))));
  }

  @ParameterizedTest(name = "{0} / {1} / deep={2}")
  @MethodSource("matrix")
  void matchesOnlyTheDeclaredArtifacts(String query, String user, boolean deep, Set<String> expected) throws Exception {
    var result = deep
        ? worker.searchDeep(context(user), query, List.of("instance"), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, List.of("name"), 100, 0)
        : worker.search(context(user), query, List.of("instance"), ResourceVersionFilter.ALL,
            ResourcePublicationStatusFilter.ALL, null, List.of("name"), 100, 0);
    List<String> ids = result.getHits().stream().map(SearchHit::getId).toList();
    assertEquals(expected.size(), result.getTotalCount());
    assertEquals(expected.size(), ids.size());
    assertEquals(expected, Set.copyOf(ids));
  }

  static Stream<Arguments> sharingMatrix() {
    return Stream.of(SearchSharingFilter.values()).flatMap(sharing -> Stream.of("owner", "reader")
        .flatMap(user -> Stream.of("search", "search-deep", "continuation")
            .map(path -> Arguments.of(sharing, user, path))));
  }

  @ParameterizedTest(name = "{0} / {1} / {2}")
  @MethodSource("sharingMatrix")
  void sharingConstrainsCountsAndEveryPage(SearchSharingFilter sharing, String user, String path) throws Exception {
    Set<String> shared = Set.of("shared-VIEWER", "shared-EDITOR", "shared-MANAGER");
    Set<String> expected = switch (sharing) {
      case SHARED_WITH_EVERYBODY -> Set.of("public-read", "public-write");
      case SHARED_WITH_ME -> user.equals("reader") ? shared : Set.of();
      case ALL -> Stream.concat(shared.stream(), Stream.of("public-read", "public-write",
          user.equals("reader") ? "reader-owned" : "owner-private")).collect(Collectors.toSet());
    };
    List<String> ids = new ArrayList<>();
    if (path.equals("continuation")) {
      String pit = null;
      Object[] after = null;
      try {
        for (int page = 0; page < 10; page++) {
          var result = worker.searchDeepPage(context(user), "Notes:Sharingprobe", List.of("instance"),
              ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, null, List.of("name"),
              2, pit, after, ModifiedDateRange.ALL, sharing);
          if (page == 0) assertEquals(expected.size(), result.result().getTotalCount());
          ids.addAll(result.result().getHits().stream().map(SearchHit::getId).toList());
          pit = result.pointInTimeId(); after = result.nextSearchAfter();
          if (pit == null) break;
        }
        assertNull(pit, "Continuation must exhaust the scoped result set");
      } finally { if (pit != null) client.deletePit(new org.opensearch.action.search.DeletePitRequest(pit), RequestOptions.DEFAULT); }
    } else {
      var result = path.equals("search-deep")
          ? worker.searchDeep(context(user), "Notes:Sharingprobe", List.of("instance"), ResourceVersionFilter.ALL,
              ResourcePublicationStatusFilter.ALL, null, List.of("name"), 100, 0, ModifiedDateRange.ALL, sharing)
          : worker.search(context(user), "Notes:Sharingprobe", List.of("instance"), ResourceVersionFilter.ALL,
              ResourcePublicationStatusFilter.ALL, null, List.of("name"), 100, 0, ModifiedDateRange.ALL, sharing);
      assertEquals(expected.size(), result.getTotalCount());
      ids.addAll(result.getHits().stream().map(SearchHit::getId).toList());
    }
    assertEquals(expected.size(), ids.size(), "No duplicate results across pages");
    assertEquals(expected, Set.copyOf(ids));
  }

  @AfterAll
  static void cleanup() throws Exception {
    if (client == null) return;
    try { client.indices().delete(new DeleteIndexRequest(INDEX), RequestOptions.DEFAULT); }
    finally { client.close(); }
  }

  @org.junit.jupiter.api.Test
  void equalNamesUseTheSameIdentifierOrderAcrossOrdinaryAndDeepPages() throws Exception {
    for (boolean deep : List.of(false, true)) {
      List<String> ids = new ArrayList<>();
      for (int offset = 0; offset < 2; offset++) {
        var result = deep
            ? worker.searchDeep(context("reader"), "\"Paging tie\"", List.of("instance"), ResourceVersionFilter.ALL,
                ResourcePublicationStatusFilter.ALL, null, List.of("name"), 1, offset)
            : worker.search(context("reader"), "\"Paging tie\"", List.of("instance"), ResourceVersionFilter.ALL,
                ResourcePublicationStatusFilter.ALL, null, List.of("name"), 1, offset);
        assertEquals(2, result.getTotalCount());
        ids.addAll(result.getHits().stream().map(SearchHit::getId).toList());
      }
      assertEquals(List.of("tie-a", "tie-z"), ids);
    }
  }
}
