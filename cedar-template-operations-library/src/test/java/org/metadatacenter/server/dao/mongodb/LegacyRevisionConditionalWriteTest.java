package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import de.flapdoodle.embed.mongo.commands.ServerAddress;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.server.dao.ArtifactRevisionConflictException;
import org.metadatacenter.util.json.JsonMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Conditional writes of a document stored before optimistic locking, which carries no revision field,
 * against a real MongoDB.
 *
 * <p>Such a document reads as revision zero, so its first conditional update or delete names revision
 * zero. The filter for that revision has to match a document that lacks the field: a filter on a stored
 * zero would match nothing, and every such artifact would answer 412 to its first edit for ever. A
 * filter without the revision would let a stale writer replace or delete it. Each case runs once per
 * artifact collection.
 */
@Tag("integration")
class LegacyRevisionConditionalWriteTest {

  private static final String DATABASE = "cedar";

  private static TransitionWalker.ReachedState<RunningMongodProcess> mongod;
  private static MongoClient client;

  @BeforeAll
  static void startMongo() {
    mongod = Mongod.instance().start(Version.Main.V5_0);
    ServerAddress address = mongod.current().getServerAddress();
    client = MongoClients.create("mongodb://" + address.getHost() + ":" + address.getPort());
  }

  @AfterAll
  static void stopMongo() {
    if (client != null) {
      client.close();
    }
    if (mongod != null) {
      mongod.close();
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"template-fields", "template-elements", "templates", "template-instances"})
  void theFirstConditionalUpdateOfARevisionlessDocumentIsAtRevisionZero(String collection) throws Exception {
    GenericLDDaoMongoDB dao = dao(collection);
    String id = storeWithoutRevision(collection, "as stored before locking");

    assertEquals(0L, dao.getRevision(id), "a document without the field reads as revision zero");
    assertEquals(0L, dao.findWithRevision(id).revision());

    dao.update(id, named(id, "first edit"), 0L);

    assertEquals(1L, dao.getRevision(id), "the first conditional update gives the document revision one");
    assertEquals("first edit", dao.find(id).get("schema:name").asText());
    assertThrows(ArtifactRevisionConflictException.class, () -> dao.update(id, named(id, "stale edit"), 0L),
        "a second writer that read revision zero must be refused");
    assertEquals("first edit", dao.find(id).get("schema:name").asText(), "the refused write changes nothing");
  }

  @ParameterizedTest
  @ValueSource(strings = {"template-fields", "template-elements", "templates", "template-instances"})
  void aRevisionlessDocumentIsDeletedAtRevisionZero(String collection) throws Exception {
    GenericLDDaoMongoDB dao = dao(collection);
    String id = storeWithoutRevision(collection, "deleted as stored");

    dao.delete(id, 0L);

    assertFalse(dao.exists(id), "a conditional delete at revision zero removes the document");
  }

  @ParameterizedTest
  @ValueSource(strings = {"template-fields", "template-elements", "templates", "template-instances"})
  void aDeleteAtRevisionZeroIsRefusedOnceTheDocumentHasBeenEdited(String collection) throws Exception {
    GenericLDDaoMongoDB dao = dao(collection);
    String id = storeWithoutRevision(collection, "edited, then deleted");
    dao.update(id, named(id, "edited"), 0L);

    assertThrows(ArtifactRevisionConflictException.class, () -> dao.delete(id, 0L),
        "a delete from a reader of revision zero must not remove the edited document");
    assertTrue(dao.exists(id));
    assertEquals("edited", dao.find(id).get("schema:name").asText());

    dao.delete(id, 1L);
    assertFalse(dao.exists(id));
  }

  private static GenericLDDaoMongoDB dao(String collection) {
    MongoCollection<Document> stored = client.getDatabase(DATABASE).getCollection(collection);
    stored.createIndex(Indexes.ascending("@id"), new IndexOptions().unique(true));
    return new GenericLDDaoMongoDB(client, DATABASE, collection);
  }

  /** Inserts a document the way the store held it before revisions existed: with no revision field. */
  private static String storeWithoutRevision(String collection, String name) {
    String id = "https://repo.metadatacenter.orgx/" + collection + "/" + UUID.randomUUID();
    client.getDatabase(DATABASE).getCollection(collection)
        .insertOne(new Document("@id", id).append("schema:name", name));
    return id;
  }

  private static JsonNode named(String id, String name) throws Exception {
    return JsonMapper.STRICT_MAPPER.createObjectNode().put("@id", id).put("schema:name", name);
  }
}
