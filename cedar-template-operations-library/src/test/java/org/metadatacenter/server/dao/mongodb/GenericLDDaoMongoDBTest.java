package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import com.mongodb.client.result.DeleteResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.dao.ArtifactRevisionConflictException;
import org.metadatacenter.util.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(history.findOneAndUpdate(any(Bson.class), any(Bson.class),
        any(com.mongodb.client.model.FindOneAndUpdateOptions.class)))
        .thenReturn(new Document("revision", 1L));
    when(collection.replaceOne(any(Bson.class), any(Document.class)))
        .thenReturn(UpdateResult.acknowledged(1L, 1L, null));
    when(collection.find(any(Bson.class))).thenReturn(laterRead);
    when(laterRead.first()).thenReturn(new Document("@id", "artifact-id")
        .append("schema:name", "second writer")
        .append(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, 3L));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode submitted = JsonMapper.STRICT_MAPPER.readTree("""
        {"@id":"artifact-id","schema:name":"first writer"}
        """);

    JsonNode returned = dao.update("artifact-id", submitted, 1L);

    assertEquals(submitted, returned);
    assertFalse(returned.has(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD));
  }

  @Test
  void staleConditionalDeletePreservesTheNewerDocument() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    FindIterable<Document> currentRead = mock(FindIterable.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(history.findOneAndUpdate(any(Bson.class), any(Bson.class),
        any(com.mongodb.client.model.FindOneAndUpdateOptions.class)))
        .thenReturn(new Document("revision", 1L));
    when(collection.deleteOne(any(Bson.class))).thenReturn(DeleteResult.acknowledged(0L));
    when(collection.find(any(Bson.class))).thenReturn(currentRead);
    when(currentRead.first()).thenReturn(new Document("@id", "artifact-id")
        .append("schema:name", "newer writer")
        .append(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, 2L));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");

    assertThrows(ArtifactRevisionConflictException.class,
        () -> dao.delete("artifact-id", 1L));
  }

  @Test
  void insertRejectedByTheUniqueIdentifierIndexIsAConflict() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(history.findOneAndUpdate(any(Bson.class), any(Bson.class),
        any(com.mongodb.client.model.FindOneAndUpdateOptions.class)))
        .thenReturn(new Document("revision", 1L));
    when(collection.insertOne(any(Document.class))).thenThrow(new MongoWriteException(
        new WriteError(11000, "E11000 duplicate key error collection: test-db.artifacts index: @id_1",
            new BsonDocument()), new ServerAddress()));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode submitted = JsonMapper.STRICT_MAPPER.readTree("""
        {"@id":"artifact-id","schema:name":"second writer"}
        """);

    ArtifactRevisionConflictException conflict = assertThrows(ArtifactRevisionConflictException.class,
        () -> dao.create(submitted));
    assertTrue(conflict.getMessage().contains("artifact-id"));
  }

  @Test
  void otherWriteErrorsOnInsertAreNotConflicts() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(history.findOneAndUpdate(any(Bson.class), any(Bson.class),
        any(com.mongodb.client.model.FindOneAndUpdateOptions.class)))
        .thenReturn(new Document("revision", 1L));
    when(collection.insertOne(any(Document.class))).thenThrow(new MongoWriteException(
        new WriteError(121, "Document failed validation", new BsonDocument()), new ServerAddress()));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode submitted = JsonMapper.STRICT_MAPPER.readTree("""
        {"@id":"artifact-id","schema:name":"writer"}
        """);

    assertThrows(MongoWriteException.class, () -> dao.create(submitted));
  }

  /*
   * Since release 2.9.25 the shared JSON policy reads a fractional number as a BigDecimal, which the driver
   * stores as a Decimal128. Rendered as extended JSON, that came back as {"$numberDecimal": "1E-7"}: an
   * object where the artifact had a number, which the validator refuses and no editor can read.
   */
  private static final String FRACTIONS = """
      {"@id":"artifact-id","_valueConstraints":{"minValue":1e-07,"maxValue":0.000001,"step":42.50,"count":3},
       "options":[0.5,-2.25]}
      """;

  @Test
  void aFractionalNumberIsReturnedAsTheNumberThatWasWritten() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(history.findOneAndUpdate(any(Bson.class), any(Bson.class),
        any(com.mongodb.client.model.FindOneAndUpdateOptions.class)))
        .thenReturn(new Document("revision", 1L));
    when(collection.replaceOne(any(Bson.class), any(Document.class)))
        .thenReturn(UpdateResult.acknowledged(1L, 1L, null));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode submitted = JsonMapper.STRICT_MAPPER.readTree(FRACTIONS);

    JsonNode returned = dao.update("artifact-id", submitted, 1L);

    // Equal as JSON and as exact decimals, scale included: 42.50 stays 42.50.
    assertEquals(submitted, returned);
    assertTrue(returned.at("/_valueConstraints/minValue").isNumber());
  }

  @Test
  void aStoredDecimal128IsReadAsTheNumberItHolds() throws Exception {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    @SuppressWarnings("unchecked")
    MongoCollection<Document> collection = mock(MongoCollection.class);
    @SuppressWarnings("unchecked")
    FindIterable<Document> read = mock(FindIterable.class);
    when(client.getDatabase("test-db")).thenReturn(database);
    when(database.getCollection("artifacts")).thenReturn(collection);
    MongoCollection<Document> history = mock(MongoCollection.class);
    when(database.getCollection("artifacts_revision_history")).thenReturn(history);
    when(collection.find(any(Bson.class))).thenReturn(read);
    // What production stores for a template saved with fractional bounds since 2.9.25.
    when(read.first()).thenReturn(new Document("@id", "artifact-id")
        .append("_valueConstraints", new Document("minValue", new Decimal128(new BigDecimal("1E-7")))
            .append("maxValue", new Decimal128(new BigDecimal("0.000001")))
            .append("step", new Decimal128(new BigDecimal("42.50")))
            .append("count", 3))
        .append("options", List.of(new Decimal128(new BigDecimal("0.5")), new Decimal128(new BigDecimal("-2.25"))))
        .append(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, 1L));

    GenericLDDaoMongoDB dao = new GenericLDDaoMongoDB(client, "test-db", "artifacts");
    JsonNode found = dao.find("artifact-id");

    assertEquals(JsonMapper.STRICT_MAPPER.readTree(FRACTIONS), found);
    assertFalse(found.toString().contains("$numberDecimal"), found.toString());
  }

  @Test
  void aDecimal128ThatIsNotANumberKeepsItsExtendedJsonForm() {
    String json = new Document("value", Decimal128.NaN).toJson(GenericLDDaoMongoDB.PUBLIC_JSON);
    assertEquals("{\"value\": {\"$numberDecimal\": \"NaN\"}}", json);
  }
}
