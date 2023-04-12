package org.metadatacenter.server.neo4j.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.util.json.JsonMapper;

import java.util.Iterator;
import java.util.Map;

public class Neo4JUtil {

  private static final String ESCAPED_ID = "_id";

  private Neo4JUtil() {
  }

  public static String escapePropertyName(String propertyName) {
    if (NodeProperty.Label.ID.equals(propertyName)) {
      return ESCAPED_ID;
    } else {
      return propertyName.replace(":", "_");
    }
  }

  public static JsonNode unescapeTopLevelPropertyNames(JsonNode node) {
    ObjectNode r = JsonMapper.MAPPER.createObjectNode();
    Iterator<Map.Entry<String, JsonNode>> nodes = node.fields();
    while (nodes.hasNext()) {
      Map.Entry<String, JsonNode> entry = nodes.next();
      String newPropertyName = unescapePropertyName(entry.getKey());
      r.set(newPropertyName, entry.getValue());
    }
    return r;
  }

  private static String unescapePropertyName(String propertyName) {
    if (ESCAPED_ID.equals(propertyName)) {
      return NodeProperty.Label.ID;
    } else {
      return propertyName.replace("_", ":");
    }
  }
}
