package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClient;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.model.Projections;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.GenericDao;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.json.JsonUtils;
import org.metadatacenter.util.mongo.FixMongoDirection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.mongodb.client.model.Filters.eq;

/**
 * Service to manage elements in a MongoDB database
 */
public class GenericLDDaoMongoDB implements GenericDao<String, JsonNode> {

  protected final MongoCollection<Document> entityCollection;
  private final JsonUtils jsonUtils;

  public GenericLDDaoMongoDB(MongoClient mongoClient, String dbName, String collectionName) {
    entityCollection = mongoClient.getDatabase(dbName).getCollection(collectionName);
    jsonUtils = new JsonUtils();
  }

  /* CRUD operations */

  /**
   * Create an element that contains a Linked Data identifier field (@id in JSON-LD). It is necessary to check that
   * there are not other elements into the DB with the same @id.
   *
   * @param element An element
   * @return The created element
   * @throws IOException If an occurs during creation
   */
  @Override
  public JsonNode create(JsonNode element) throws IOException {
    // Adapts all keys not accepted by MongoDB
    JsonNode fixedElement = jsonUtils.fixMongoDB(element, FixMongoDirection.WRITE_TO_MONGO);
    Map<String, Object> elementMap = JsonMapper.MAPPER.convertValue(fixedElement, Map.class);
    Document elementDoc = new Document(elementMap);
    entityCollection.insertOne(elementDoc);
    // Returns the document created (all keys adapted for MongoDB are restored)
    return jsonUtils.fixMongoDB(JsonMapper.MAPPER.readTree(elementDoc.toJson()), FixMongoDirection.READ_FROM_MONGO);
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
      Bson fields = null;
      switch (includeExclude) {
        case INCLUDE:
          fields = Projections.fields(Projections.include(fieldNames), Projections.excludeId());
          break;
        case EXCLUDE:
          fields = Projections.exclude(fieldNames);
          break;
      }
      if (fields != null) {
        findIterable.projection(fields);
      }
    }
    List<JsonNode> docs = new ArrayList<>();
    try (MongoCursor<Document> cursor = findIterable.iterator()) {
      while (cursor.hasNext()) {
        JsonNode node = jsonUtils.fixMongoDB(JsonMapper.MAPPER.readTree(cursor.next().toJson()), FixMongoDirection
            .READ_FROM_MONGO);
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
    if ((id == null) || (id.length() == 0)) {
      throw new IllegalArgumentException();
    }
    Document doc = entityCollection.find(eq("@id", id)).first();
    if (doc == null) {
      return null;
    }
    return jsonUtils.fixMongoDB(JsonMapper.MAPPER.readTree(doc.toJson()), FixMongoDirection.READ_FROM_MONGO);
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
  public JsonNode update(String id, JsonNode content)
      throws ArtifactServerResourceNotFoundException, IOException {
    if ((id == null) || (id.length() == 0)) {
      throw new IllegalArgumentException();
    }
    if (!exists(id)) {
      throw new ArtifactServerResourceNotFoundException();
    }
    // Adapts all keys not accepted by MongoDB
    content = jsonUtils.fixMongoDB(content, FixMongoDirection.WRITE_TO_MONGO);
    Map<String, Object> contentMap = JsonMapper.MAPPER.convertValue(content, Map.class);
    Document contentDocument = new Document(contentMap);
    UpdateResult updateResult = entityCollection.replaceOne(eq("@id", id), contentDocument);
    if (updateResult.getMatchedCount() == 1) {
      return find(id);
    } else {
      throw new InternalError();
    }
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
    if (!exists(id)) {
      throw new ArtifactServerResourceNotFoundException();
    }
    DeleteResult deleteResult = entityCollection.deleteOne(eq("@id", id));
    if (deleteResult.getDeletedCount() != 1) {
      throw new InternalError();
    }
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
    entityCollection.drop();
  }

  @Override
  public long count() {
    return entityCollection.count();
  }

}
