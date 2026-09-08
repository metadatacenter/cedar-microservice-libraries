package org.metadatacenter.util.artifact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.ModelNodeNames;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArtifactDocumentSchemaTest {

  @Test
  void everyDocumentedKeyIsAModelKey() {
    Set<String> modelKeys = new LinkedHashSet<>(ModelNodeNames.SCHEMA_ARTIFACT_KEYWORDS);
    modelKeys.add(ModelNodeNames.SCHEMA_IS_BASED_ON);
    for (Class<?> documented : Arrays.asList(ArtifactDocument.class, SchemaArtifactDocument.class,
        InstanceArtifactDocument.class)) {
      for (String key : documentedKeys(documented)) {
        assertTrue(modelKeys.contains(key), documented.getSimpleName() + " documents " + key
            + ", which the model does not define");
      }
    }
  }

  @Test
  void documentedStatusesMatchTheirWireValues() throws Exception {
    Field status = SchemaArtifactDocument.class.getField("status");
    Set<String> documented = new LinkedHashSet<>(Arrays.asList(
        status.getAnnotation(Schema.class).allowableValues()));
    Set<String> wire = Arrays.stream(BiboStatus.values()).map(BiboStatus::getValue)
        .collect(Collectors.toCollection(LinkedHashSet::new));
    assertEquals(wire, documented);
  }

  @Test
  void documentedTypesAreTheSchemaArtifactTypes() throws Exception {
    Field type = SchemaArtifactDocument.class.getField("type");
    Set<String> documented = new LinkedHashSet<>(Arrays.asList(
        type.getAnnotation(Schema.class).allowableValues()));
    assertEquals(ModelNodeNames.SCHEMA_ARTIFACT_TYPE_IRIS, documented);
  }

  private static Set<String> documentedKeys(Class<?> documented) {
    Set<String> keys = new LinkedHashSet<>();
    for (Field field : documented.getFields()) {
      Schema schema = field.getAnnotation(Schema.class);
      if (schema != null) {
        keys.add(schema.name());
      }
    }
    return keys;
  }
}
