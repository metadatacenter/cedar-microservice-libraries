package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.ErrorCategory;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Projections;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactRevisionConflictException;
import org.metadatacenter.server.dao.ArtifactWithRevision;
import org.metadatacenter.server.dao.GenericDao;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.json.JsonUtils;
import org.metadatacenter.util.mongo.FixMongoDirection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Service to manage elements in a MongoDB database
 */
public class GenericLDDaoMongoDB implements GenericDao<String, JsonNode> {

  static final String INTERNAL_REVISION_FIELD = "_cedarRevision";

  protected final MongoCollection<Document> entityCollection;
  private final MongoCollection<Document> revisionCollection;
  private final JsonUtils jsonUtils;

  public GenericLDDaoMongoDB(MongoClient mongoClient, String dbName, String collectionName) {
    entityCollection = mongoClient.getDatabase(dbName).getCollection(collectionName);
    revisionCollection = mongoClient.getDatabase(dbName).getCollection(collectionName + "_revision_history");
    jsonUtils = new JsonUtils();
  }

  /* CRUD operations */

  /**
   * Create an element that contains a Linked Data identifier field (@id in JSON-LD). No two documents in a
   * collection may share that identifier. The unique index on it, which the admin tool's
   * {@code artifactServer-initDB} task provisions, enforces this at the store; a read that found the
   * identifier absent followed by an insert is therefore safe against a concurrent writer, whose insert the
   * index rejects. That rejection surfaces as an {@link ArtifactRevisionConflictException}, the same signal a
   * revision-qualified update or delete gives when the store moved on after the read.
   *
   * @param element An element
   * @return The created element
   * @throws ArtifactRevisionConflictException If a document with the same @id already exists
   * @throws IOException                       If an error occurs during creation
   */
  @Override
  public JsonNode create(JsonNode element) throws IOException {
    return createWithRevision(element).content();
  }

  public ArtifactWithRevision<JsonNode> createWithRevision(JsonNode element) throws IOException {
    // Adapts all keys not accepted by MongoDB
    JsonNode fixedElement = jsonUtils.fixMongoDB(element, FixMongoDirection.WRITE_TO_MONGO);
    Map<String, Object> elementMap = JsonMapper.STRICT_MAPPER.convertValue(fixedElement, Map.class);
    Document elementDoc = new Document(elementMap);
    elementDoc.remove(TemplateReferenceGuard.DELETION_TOKEN);
    elementDoc.put(INTERNAL_REVISION_FIELD, nextIncarnationRevision(String.valueOf(elementDoc.get("@id"))));
    try {
      entityCollection.insertOne(elementDoc);
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() == ErrorCategory.DUPLICATE_KEY) {
        throw new ArtifactRevisionConflictException(String.valueOf(elementDoc.get("@id")));
      }
      throw e;
    }
    // Returns the document created (all keys adapted for MongoDB are restored)
    return new ArtifactWithRevision<>(toPublicJson(elementDoc), elementDoc.get(INTERNAL_REVISION_FIELD, Number.class).longValue());
  }

  /**
   * Find all elements
   *
   * @return A list of elements
   * @throws IOException If an error occurs during retrieval
   */
  @Override
  public List<JsonNode> findAll() throws IOException {
    return findAll(null, null, null, FieldNameInEx.UNDEFINED);
  }

  @Override
  public List<JsonNode> findAll(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException {
    return findAll(null, null, fieldNames, includeExclude);
  }

  @Override
  public List<JsonNode> findAll(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx includeExclude)
      throws IOException {
    FindIterable<Document> findIterable = entityCollection.find();
    if (limit != null) {
      findIterable.limit(limit);
    }
    if (offset != null) {
      findIterable.skip(offset);
    }
    if (fieldNames != null && fieldNames.size() > 0) {
      Bson fields = switch (includeExclude) {
        case INCLUDE -> Projections.fields(Projections.include(fieldNames), Projections.excludeId());
        case EXCLUDE -> Projections.exclude(fieldNames);
        default -> null;
      };
      if (fields != null) {
        findIterable.projection(fields);
      }
    }
    List<JsonNode> docs = new ArrayList<>();
    try (MongoCursor<Document> cursor = findIterable.iterator()) {
      while (cursor.hasNext()) {
        JsonNode node = toPublicJson(cursor.next());
        docs.add(node);
      }
    }
    return docs;
  }

  /**
   * Find an element using its linked data ID  (@id in JSON-LD)
   *
   * @param id The linked data ID of the element
   * @return A JSON representation of the element or null if the element was not found
   * @throws IllegalArgumentException If the ID is not valid
   * @throws IOException              If an error occurs during retrieval
   */
  @Override
  public JsonNode find(String id) throws IOException {
    ArtifactWithRevision<JsonNode> artifact = findWithRevision(id);
    return artifact == null ? null : artifact.content();
  }

  @Override
  public ArtifactWithRevision<JsonNode> findWithRevision(String id) throws IOException {
    if ((id == null) || (id.length() == 0)) {
      throw new IllegalArgumentException();
    }
    Document doc = entityCollection.find(eq("@id", id)).first();
    if (doc == null) {
      return null;
    }
    Number revision = doc.get(INTERNAL_REVISION_FIELD, Number.class);
    return new ArtifactWithRevision<>(toPublicJson(doc), revision == null ? 0L : revision.longValue());
  }

  @Override
  public long getRevision(String id) throws ArtifactServerResourceNotFoundException {
    if ((id == null) || id.isEmpty()) {
      throw new IllegalArgumentException();
    }
    Document doc = entityCollection.find(eq("@id", id))
        .projection(Projections.include(INTERNAL_REVISION_FIELD))
        .first();
    if (doc == null) {
      throw new ArtifactServerResourceNotFoundException();
    }
    Number revision = doc.get(INTERNAL_REVISION_FIELD, Number.class);
    return revision == null ? 0L : revision.longValue();
  }

  /**
   * Update an element using its linked data ID  (@id in JSON-LD)
   *
   * @param id      The linked data ID of the element to update
   * @param content The new content of the document
   * @return The updated JSON representation of the element
   * @throws IllegalArgumentException                If the ID is not valid
   * @throws ArtifactServerResourceNotFoundException If the element is not found
   * @throws IOException                             If an error occurs during update
   */
  @Override
  public JsonNode update(String id, JsonNode content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException {
    if ((id == null) || (id.length() == 0)) {
      throw new IllegalArgumentException();
    }
    // Adapts all keys not accepted by MongoDB
    content = jsonUtils.fixMongoDB(content, FixMongoDirection.WRITE_TO_MONGO);
    Map<String, Object> contentMap = JsonMapper.STRICT_MAPPER.convertValue(content, Map.class);
    Document contentDocument = new Document(contentMap);
    contentDocument.remove(TemplateReferenceGuard.DELETION_TOKEN);
    long replacementRevision = Math.addExact(expectedRevision, 1L);
    rememberRevision(id, replacementRevision);
    contentDocument.put(INTERNAL_REVISION_FIELD, replacementRevision);
    Bson revisionFilter = expectedRevision == 0L
        ? com.mongodb.client.model.Filters.exists(INTERNAL_REVISION_FIELD, false)
        : eq(INTERNAL_REVISION_FIELD, expectedRevision);
    UpdateResult updateResult = entityCollection.replaceOne(and(eq("@id", id), revisionFilter), contentDocument);
    if (updateResult.getMatchedCount() == 1) {
      // A fresh read could observe a later writer and return a body that does not match the response ETag.
      return toPublicJson(contentDocument);
    } else if (!exists(id)) {
      throw new ArtifactServerResourceNotFoundException();
    } else {
      throw new ArtifactRevisionConflictException(id, expectedRevision);
    }
  }

  private JsonNode toPublicJson(Document storedDocument) throws IOException {
    Document publicDocument = new Document(storedDocument);
    publicDocument.remove(INTERNAL_REVISION_FIELD);
    publicDocument.remove(TemplateReferenceGuard.DELETION_TOKEN);
    return jsonUtils.fixMongoDB(JsonMapper.STRICT_MAPPER.readTree(publicDocument.toJson()),
        FixMongoDirection.READ_FROM_MONGO);
  }

  /**
   * Delete an element using its linked data ID  (@id in JSON-LD)
   *
   * @param id The linked data ID of the element to delete
   * @throws IllegalArgumentException                If the ID is not valid
   * @throws ArtifactServerResourceNotFoundException If the element is not found
   * @throws IOException                             If an error occurs during deletion
   */
  @Override
  public void delete(String id) throws ArtifactServerResourceNotFoundException, IOException {
    if ((id == null) || (id.length() == 0)) {
      throw new IllegalArgumentException();
    }
    rememberRevision(id, getRevision(id));
    DeleteResult deleteResult = entityCollection.deleteOne(eq("@id", id));
    if (deleteResult.getDeletedCount() == 0) {
      throw new ArtifactServerResourceNotFoundException();
    }
  }

  /**
   * Deletes exactly the revision the caller observed. The identifier and revision are matched by one
   * Mongo operation, so an edit committed after the caller's GET cannot be removed by a stale DELETE.
   */
  @Override
  public void delete(String id, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException {
    deleteMatching(id, expectedRevision, new Document());
  }

  void deleteMatching(String id, long expectedRevision, Bson condition)
      throws ArtifactServerResourceNotFoundException, IOException {
    if ((id == null) || id.isEmpty()) {
      throw new IllegalArgumentException();
    }
    rememberRevision(id, expectedRevision);
    Bson revisionFilter = expectedRevision == 0L
        ? com.mongodb.client.model.Filters.exists(INTERNAL_REVISION_FIELD, false)
        : eq(INTERNAL_REVISION_FIELD, expectedRevision);
    DeleteResult deleteResult = entityCollection.deleteOne(and(eq("@id", id), revisionFilter, condition));
    if (deleteResult.getDeletedCount() == 1) {
      return;
    }
    if (!exists(id)) {
      throw new ArtifactServerResourceNotFoundException();
    }
    throw new ArtifactRevisionConflictException(id, expectedRevision);
  }

  /**
   * Check if an element exists using its linked data ID  (@id in JSON-LD)
   *
   * @param id The linked data ID of the element
   * @return True if an element with the supplied linked data ID  exists or False otherwise
   * @throws IOException If an error occurs during the existence check
   */
  @Override
  public boolean exists(String id) throws IOException {
    return (find(id) != null);
  }

  /**
   * Delete all elements
   */
  @Override
  public void deleteAll() {
    // Keep the revision ledger: even bulk deletion must not make an old validator current again.
    try (var cursor = entityCollection.find().projection(Projections.include("@id", INTERNAL_REVISION_FIELD)).iterator()) {
      while (cursor.hasNext()) {
        Document document = cursor.next();
        Number revision = document.get(INTERNAL_REVISION_FIELD, Number.class);
        rememberRevision(document.getString("@id"), revision == null ? 0 : revision.longValue());
      }
    }
    entityCollection.drop();
  }

  private long nextIncarnationRevision(String id) {
    return revisionCollection.findOneAndUpdate(eq("_id", id), Updates.inc("revision", 1L),
        new FindOneAndUpdateOptions().upsert(true).returnDocument(ReturnDocument.AFTER))
        .get("revision", Number.class).longValue();
  }

  /** Record the high-water mark before the document mutation, including for legacy documents. */
  private void rememberRevision(String id, long revision) {
    try {
      revisionCollection.updateOne(eq("_id", id), Updates.max("revision", revision),
          new UpdateOptions().upsert(true));
    } catch (MongoWriteException e) {
      if (e.getError().getCategory() != ErrorCategory.DUPLICATE_KEY) throw e;
      // Another process provisioned this legacy identifier's ledger concurrently.
      revisionCollection.updateOne(eq("_id", id), Updates.max("revision", revision));
    }
  }

  @Override
  public long count() {
    return entityCollection.countDocuments();
  }

}
