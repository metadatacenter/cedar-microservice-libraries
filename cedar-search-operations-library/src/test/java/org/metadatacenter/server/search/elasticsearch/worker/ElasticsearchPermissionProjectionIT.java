package org.metadatacenter.server.search.elasticsearch.worker;

import org.apache.http.HttpHost;
import org.junit.jupiter.api.Test;
import org.metadatacenter.id.CedarUntypedFilesystemResourceId;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.action.admin.indices.delete.DeleteIndexRequest;
import org.opensearch.action.admin.indices.refresh.RefreshRequest;
import org.opensearch.action.get.GetRequest;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.indices.CreateIndexRequest;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in OpenSearch check; the normal backend-free build does not run *IT classes. */
class ElasticsearchPermissionProjectionIT {
  @Test
  void projectionPreservesContentReplacesPermissionsAndRemovesLegacyCopies() throws Exception {
    String index = "cedar-permission-it-" + UUID.randomUUID();
    String host = System.getenv().getOrDefault("CEDAR_OPENSEARCH_HOST", "127.0.0.1");
    int port = Integer.parseInt(System.getenv().getOrDefault("CEDAR_OPENSEARCH_REST_PORT", "9200"));
    try (var client = new RestHighLevelClient(RestClient.builder(new HttpHost(host, port, "http")))) {
      client.indices().create(new CreateIndexRequest(index)
          .settings(Map.of("index.refresh_interval", "-1"))
          .mapping(Map.of("properties", Map.of("cid", Map.of("type", "keyword")))), RequestOptions.DEFAULT);
      try {
        var worker = new ElasticsearchIndexingWorker(index, client);
        var document = JsonMapper.STRICT_MAPPER.readTree("""
            {"cid":"resource-1", "info":{"name":"name", "obsolete":"remove me"},
             "users":["old-viewer"], "groups":["old-group"], "computedEverybodyPermission":"read",
             "infoFields":[{"fieldName":"preserved content"}], "possibleValues":{"valueLabels":["preserved"]}}
            """);
        worker.addToIndex(document, "resource-1");
        worker.addToIndex(document, "legacy-generated-id");
        client.indices().refresh(new RefreshRequest(index), RequestOptions.DEFAULT);
        assertEquals(1, worker.removeLegacyFromIndex(CedarUntypedFilesystemResourceId.build("resource-1")));
        var patch = JsonMapper.STRICT_MAPPER.readTree("""
            {"info":{"name":"name", "parentFolderId":"new-folder"}, "users":["new-viewer"],
             "groups":[], "categories":[], "computedEverybodyPermission":null}
            """);
        assertTrue(worker.updatePermissionProjection(patch, "resource-1"));
        assertEquals(patch.get("users"), searchable(client, index, "resource-1").get("users"),
            "visibility must not depend on a periodic refresh");
        var stored = JsonMapper.STRICT_MAPPER.readTree(
            client.get(new GetRequest(index, "resource-1"), RequestOptions.DEFAULT).getSourceAsString());
        assertEquals(document.get("infoFields"), stored.get("infoFields"));
        assertEquals(document.get("possibleValues"), stored.get("possibleValues"));
        assertEquals(patch.get("users"), stored.get("users"));
        assertTrue(stored.get("groups").isEmpty());
        assertTrue(stored.get("computedEverybodyPermission").isNull());
        assertFalse(stored.get("info").has("obsolete"));
        assertEquals("new-folder", stored.get("info").get("parentFolderId").asText());
        assertFalse(client.get(new GetRequest(index, "legacy-generated-id"), RequestOptions.DEFAULT).isExists());
        var second = ((com.fasterxml.jackson.databind.node.ObjectNode) document.deepCopy()).put("cid", "resource-2");
        worker.addToIndex(second, "resource-2");
        assertEquals(java.util.Set.of("missing-bulk-resource"), worker.updatePermissionProjections(Map.of(
            "resource-1", patch, "resource-2", patch, "missing-bulk-resource", patch)));
        worker.refreshIndex();
        assertEquals(patch.get("users"), searchable(client, index, "resource-2").get("users"));
        var bulkStored = JsonMapper.STRICT_MAPPER.readTree(
            client.get(new GetRequest(index, "resource-2"), RequestOptions.DEFAULT).getSourceAsString());
        assertEquals(second.get("infoFields"), bulkStored.get("infoFields"));
        assertEquals(patch.get("users"), bulkStored.get("users"));
        assertFalse(bulkStored.get("info").has("obsolete"));
        assertFalse(client.get(new GetRequest(index, "missing-bulk-resource"), RequestOptions.DEFAULT).isExists());
        var invalid = JsonMapper.STRICT_MAPPER.readTree("{\"info\":\"not an object\"}");
        assertThrows(org.metadatacenter.exception.CedarProcessingException.class,
            () -> worker.updatePermissionProjections(Map.of("resource-1", patch, "resource-2", invalid)),
            "a partial bulk failure must prevent acknowledgement of the durable batch");
        assertFalse(worker.updatePermissionProjection(patch, "missing-resource"));
        assertFalse(client.get(new GetRequest(index, "missing-resource"), RequestOptions.DEFAULT).isExists(),
            "the caller must reconstruct a missing document, not leave a partial document in search");
      } finally {
        client.indices().delete(new DeleteIndexRequest(index), RequestOptions.DEFAULT);
      }
    }
  }

  private static com.fasterxml.jackson.databind.JsonNode searchable(RestHighLevelClient client, String index, String id)
      throws Exception {
    var response = client.search(new org.opensearch.action.search.SearchRequest(index).source(
        new org.opensearch.search.builder.SearchSourceBuilder().query(
            org.opensearch.index.query.QueryBuilders.idsQuery().addIds(id))), RequestOptions.DEFAULT);
    assertEquals(1, response.getHits().getHits().length);
    return JsonMapper.STRICT_MAPPER.readTree(response.getHits().getAt(0).getSourceAsString());
  }
}
