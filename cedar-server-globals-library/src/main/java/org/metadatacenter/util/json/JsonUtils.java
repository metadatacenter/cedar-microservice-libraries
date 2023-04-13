package org.metadatacenter.util.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import org.metadatacenter.util.mongo.FixMongoDirection;

public class JsonUtils {

  /* Fix for the keywords not allowed by MongoDB (e.g. $schema) */
  // Rename JSON field to be stored into MongoDB
  // direction: WRITE_TO_MONGO  -> update field names for MongoDB storage (e.g. $schema -> _$schema)
  // direction: READ_FROM_MONGO -> update field names after reading them from MongoDB (e.g. _$schema -> $schema)
  public JsonNode fixMongoDB(JsonNode node, FixMongoDirection direction) {
    boolean reverse = false;
    if (direction == FixMongoDirection.READ_FROM_MONGO) {
      reverse = true;
    }
    updateFieldName(node, "$schema", "_$schema", reverse);
    updateFieldName(node, "$oid", "_$oid", reverse);
    updateFieldName(node, "$numberLong", "_$numberLong", reverse);
    // Now, recursively invoke this method on all properties
    for (JsonNode child : node) {
      fixMongoDB(child, direction);
    }
    return node;
  }

  private JsonNode updateFieldName(JsonNode node, String fieldName, String newFieldName,
                                   boolean reverse) {
    if (reverse) {
      String swap = fieldName;
      fieldName = newFieldName;
      newFieldName = swap;
    }
    if (node.has(fieldName)) {
      ((ObjectNode) node).set(newFieldName, new TextNode(node.get(fieldName).asText()));
      ((ObjectNode) node).remove(fieldName);
    }
    return node;
  }

  // Remove a particular field from a JsonNode object
  public static JsonNode removeField(JsonNode node, String fieldName) {
    ObjectNode object = (ObjectNode) node;
    object.remove(fieldName);
    return object;
  }

}
