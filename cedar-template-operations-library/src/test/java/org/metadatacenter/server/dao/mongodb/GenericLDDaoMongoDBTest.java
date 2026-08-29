package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.metadatacenter.util.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GenericLDDaoMongoDBTest {

  @Test
  void successfulUpdateReturnsTheRevisionItWroteEvenIfAReadWouldSeeANewerRevision() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    FindIterable<Document> laterRead = mock(FindIterable.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    when(collection.replaceOne(any(Bson.class), any(Document.class)))
        .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
    when(collection.find(any(Bson.class))).thenReturn(laterRead);
    when(laterRead.first()).thenReturn(new Document("@id", "artifact-id")
        .append("schema:name", "second writer")
        .append(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, 3L));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode submitted = JsonMapper.MAPPER.readTree("""
        {"@id":"artifact-id","schema:name":"first writer"}
        """);

    JsonNode returned = dao.update("artifact-id", submitted, 1L);

    assertEquals(submitted, returned);
    assertFalse(returned.has(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD));
  }
}
