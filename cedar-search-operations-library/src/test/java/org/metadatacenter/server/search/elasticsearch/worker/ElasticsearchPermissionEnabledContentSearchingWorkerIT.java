package org.metadatacenter.server.search.elasticsearch.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.OpensearchConfig;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.index.IndexRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.search.SearchHit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Executes the permission query against OpenSearch itself. The unit tests in this package prove the
 * shape of the request; this class proves what that request actually matches, which is the security
 * boundary a mocked {@code RestHighLevelClient} cannot exercise.
 */
class ElasticsearchPermissionEnabledContentSearchingWorkerIT {

  private static final String OWNER_ID = "https://metadatacenter.org/users/search-owner";
  private static final String READER_ID = "https://metadatacenter.org/users/search-reader";
  private static final String PRIVATE_ID = "private-template";
  private static final String SHARED_ID = "shared-template";
  private static final String EVERYBODY_ID = "everybody-template";
  private static final String INDEX_NAME = "cedar-search-permission-it-"
      + UUID.randomUUID().toString().toLowerCase();

  private static RestHighLevelClient client;
  private static ElasticsearchPermissionEnabledContentSearchingWorker worker;

  @BeforeAll
  static void startIndex() throws Exception {
    String host = System.getenv().getOrDefault("CEDAR_OPENSEARCH_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("CEDAR_OPENSEARCH_REST_PORT", "9200"));
    client = new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, "http")));

    CreateIndexRequest create = new CreateIndexRequest(INDEX_NAME)
        .mapping(testMapping(), XContentType.JSON);
    client.indices().create(create, RequestOptions.DEFAULT);

    OpensearchConfig config = new ObjectMapper().readValue("""
        {"indexes":{"searchIndex":{"name":"%s"}},
         "maxResultWindow":100,"searchContextKeepAlive":60000}
        """.formatted(INDEX_NAME), OpensearchConfig.class);
    worker = new ElasticsearchPermissionEnabledContentSearchingWorker(config, client);

    index(PRIVATE_ID, "permissionprobe private", List.of(readKey(OWNER_ID)), null);
    index(SHARED_ID, "permissionprobe shared", List.of(readKey(OWNER_ID), readKey(READER_ID)), null);
    index(EVERYBODY_ID, "permissionprobe everybody", List.of(readKey(OWNER_ID)),
        NodeSharePermission.READ.getValue());
    for (int i = 0; i < 5; i++) {
      index("walk-template-" + i, "walkprobe " + i, List.of(readKey(READER_ID)), null);
    }
    refresh();
  }

  @AfterAll
  static void stopIndex() throws Exception {
    if (client != null) {
      try {
        client.indices().delete(new org.opensearch.action.admin.indices.delete.DeleteIndexRequest(INDEX_NAME),
            RequestOptions.DEFAULT);
      } finally {
        client.close();
      }
    }
  }

  @Test
  void termSearchRequiresReadPermissionAndTracksGrantChanges() throws Exception {
    assertEquals(Set.of(PRIVATE_ID, SHARED_ID, EVERYBODY_ID),
        ids(search(context(OWNER_ID, false), "permissionprobe")));
    assertEquals(Set.of(SHARED_ID, EVERYBODY_ID),
        ids(search(context(READER_ID, false), "permissionprobe")));
    assertEquals(Set.of(PRIVATE_ID, SHARED_ID, EVERYBODY_ID),
        ids(search(context("https://metadatacenter.org/users/admin", true), "permissionprobe")));

    index(PRIVATE_ID, "permissionprobe private", List.of(readKey(OWNER_ID), readKey(READER_ID)), null);
    refresh();
    assertEquals(Set.of(PRIVATE_ID, SHARED_ID, EVERYBODY_ID),
        ids(search(context(READER_ID, false), "permissionprobe")));

    index(PRIVATE_ID, "permissionprobe private", List.of(readKey(OWNER_ID)), null);
    refresh();
    assertEquals(Set.of(SHARED_ID, EVERYBODY_ID),
        ids(search(context(READER_ID, false), "permissionprobe")));
  }

  @Test
  void deepSearchContinuationReturnsEveryPermittedRowExactlyOnce() throws Exception {
    CedarRequestContext context = context(READER_ID, false);
    List<String> walked = new ArrayList<>();
    String pointInTimeId = null;
    Object[] searchAfter = null;

    do {
      DeepSearchPage page = worker.searchDeepPage(context, "walkprobe", List.of("template"),
          ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, null, List.of(), 2,
          pointInTimeId, searchAfter);
      walked.addAll(page.result().getHits().stream().map(SearchHit::getId).toList());
      pointInTimeId = page.pointInTimeId();
      searchAfter = page.nextSearchAfter();
    } while (pointInTimeId != null);

    assertEquals(5, walked.size());
    assertEquals(Set.of("walk-template-0", "walk-template-1", "walk-template-2",
        "walk-template-3", "walk-template-4"), new HashSet<>(walked));
  }

  private static SearchResponseResult search(CedarRequestContext context, String query) throws Exception {
    return worker.search(context, query, List.of("template"), ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, null, List.of(), 20, 0);
  }

  private static Set<String> ids(SearchResponseResult result) {
    assertEquals(result.getHits().size(), result.getTotalCount());
    Set<String> ids = result.getHits().stream().map(SearchHit::getId).collect(java.util.stream.Collectors.toSet());
    assertFalse(ids.isEmpty());
    return ids;
  }

  private static CedarRequestContext context(String userId, boolean administrator) {
    CedarUser user = new CedarUser();
    user.setId(userId);
    if (administrator) {
      user.setPermissions(List.of(CedarPermission.READ_NOT_READABLE_NODE.getPermissionName()));
    }
    CedarRequestContext context = mock(CedarRequestContext.class);
    when(context.getCedarUser()).thenReturn(user);
    return context;
  }

  private static String readKey(String userId) {
    return CedarNodeMaterializedPermissions.getKey(userId, FilesystemResourcePermission.READ);
  }

  private static void index(String id, String name, List<String> users, String everybodyPermission)
      throws Exception {
    Map<String, Object> info = new LinkedHashMap<>();
    info.put("@id", "https://repo.metadatacenter.org/templates/" + id);
    info.put("resourceType", "template");
    info.put("schema:name", name);

    Map<String, Object> document = new LinkedHashMap<>();
    document.put("cid", info.get("@id"));
    document.put("summaryText", name);
    document.put("users", users);
    document.put("info", info);
    if (everybodyPermission != null) {
      document.put("computedEverybodyPermission", everybodyPermission);
    }

    client.index(new IndexRequest(INDEX_NAME).id(id).source(document), RequestOptions.DEFAULT);
  }

  private static void refresh() throws Exception {
    client.indices().refresh(new RefreshRequest(INDEX_NAME), RequestOptions.DEFAULT);
  }

  private static String testMapping() {
    return """
        {"properties":{
          "cid":{"type":"keyword"},
          "summaryText":{"type":"text","fields":{"raw":{"type":"text","analyzer":"standard"}}},
          "users":{"type":"keyword"},
          "computedEverybodyPermission":{"type":"keyword"},
          "info":{"properties":{
            "@id":{"type":"keyword"},
            "resourceType":{"type":"keyword"},
            "schema:name":{"type":"keyword","fields":{"raw":{"type":"text","analyzer":"standard"}}}
          }}
        }}
        """;
  }
}
