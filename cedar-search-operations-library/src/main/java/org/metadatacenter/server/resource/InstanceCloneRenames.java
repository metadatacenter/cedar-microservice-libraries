package org.metadatacenter.server.resource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Carries values across key renames using the unchanged JSON-LD property IRI. */
final class InstanceCloneRenames {
  static void apply(ObjectNode instance, JsonNode schema) {
    JsonNode properties = schema.path("properties");
    JsonNode context = instance.path("@context");
    if (!(context instanceof ObjectNode instanceContext)) return;
    Map<String, String> names = new HashMap<>();
    Set<String> ambiguous = new HashSet<>();
    properties.path("@context").path("properties").fields().forEachRemaining(entry -> {
      JsonNode values = entry.getValue().path("enum");
      if (values.size() == 1 && values.get(0).isTextual()) {
        String iri = values.get(0).asText();
        if (names.putIfAbsent(iri, entry.getKey()) != null) ambiguous.add(iri);
      }
    });
    ArrayList<String> keys = new ArrayList<>();
    instance.fieldNames().forEachRemaining(keys::add);
    for (String oldKey : keys) {
      JsonNode term = context.path(oldKey);
      String iri = term.isTextual() ? term.asText() : term.path("@id").asText("");
      String newKey = ambiguous.contains(iri) ? null : names.get(iri);
      if (newKey == null) newKey = oldKey;
      if (!newKey.equals(oldKey)) {
        if (instance.has(newKey) || instanceContext.has(newKey)) {
          throw new IllegalArgumentException("Cannot clone conflicting field rename: " + oldKey + " -> " + newKey);
        }
        instance.set(newKey, instance.remove(oldKey));
        instanceContext.set(newKey, instanceContext.remove(oldKey));
      }
      JsonNode childSchema = properties.path(newKey);
      if (childSchema.path("type").asText().equals("array")) childSchema = childSchema.path("items");
      if (!childSchema.path("properties").has("@context")) continue;
      JsonNode value = instance.get(newKey);
      if (value instanceof ObjectNode object) apply(object, childSchema);
      else if (value.isArray()) {
        for (JsonNode item : value) if (item instanceof ObjectNode object) apply(object, childSchema);
      }
    }
  }

  private InstanceCloneRenames() { }
}
