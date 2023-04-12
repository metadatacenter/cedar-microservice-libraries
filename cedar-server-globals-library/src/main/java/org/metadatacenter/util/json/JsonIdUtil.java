package org.metadatacenter.util.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.jsonld.LinkedDataUtil;

import java.util.Iterator;
import java.util.Map;

import static org.metadatacenter.model.ModelNodeNames.SCHEMA_IS_BASED_ON;

public class JsonIdUtil {
  public static JsonNode localizeAtIdsAndTemplateId(JsonNode node, LinkedDataUtil
      linkedDataUtil) {
    ObjectNode object = (ObjectNode) node;
    localizeFieldValueAsId(object, SCHEMA_IS_BASED_ON, linkedDataUtil, CedarResourceType.TEMPLATE);
    localizeAtIdRecursively(object, linkedDataUtil);
    return object;
  }

  private static void localizeAtIdRecursively(ObjectNode object, LinkedDataUtil linkedDataUtil) {
    String atType = getKeyValueIfString(object, "@type");
    CedarResourceType resourceType = CedarResourceType.forAtType(atType);
    if (resourceType != null) {
      localizeFieldValueAsId(object, "@id", linkedDataUtil, resourceType);
      Iterator<Map.Entry<String, JsonNode>> fields = object.fields();
      while (fields.hasNext()) {
        Map.Entry<String, JsonNode> entry = fields.next();
        JsonNode child = entry.getValue();
        if (child != null) {
          if (child.isObject()) {
            localizeAtIdRecursively((ObjectNode) child, linkedDataUtil);
          } else if (child.isArray()) {
            localizeAtIdRecursivelyInArray((ArrayNode) child, linkedDataUtil);
          }
        }
      }
    }
  }

  private static void localizeAtIdRecursivelyInArray(ArrayNode array, LinkedDataUtil linkedDataUtil) {
    for (int i = 0; i < array.size(); i++) {
      JsonNode element = array.get(i);
      if (element.isObject()) {
        localizeAtIdRecursively((ObjectNode) element, linkedDataUtil);
      } else if (element.isArray()) {
        localizeAtIdRecursivelyInArray((ArrayNode) element, linkedDataUtil);
      }
    }
  }

  private static void localizeFieldValueAsId(ObjectNode object, String fieldName, LinkedDataUtil
      linkedDataUtil, CedarResourceType resourceType) {
    String v = getKeyValueIfString(object, fieldName);
    if (v != null) {
      int lastPos = v.lastIndexOf('/');
      if (lastPos != -1) {
        String uuid = v.substring(lastPos + 1);
        object.put(fieldName, linkedDataUtil.getLinkedDataId(resourceType, uuid));
      }
    }
  }

  private static String getKeyValueIfString(ObjectNode object, String key) {
    JsonNode jsonNode = object.get(key);
    if (jsonNode != null) {
      if (jsonNode.isTextual()) {
        return jsonNode.asText();
      }
    }
    return null;
  }
}
