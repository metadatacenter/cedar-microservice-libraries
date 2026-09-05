package org.metadatacenter.util.http;

import io.swagger.v3.oas.annotations.media.Schema;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorReasonKey;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.error.CedarSuggestedAction;
import org.metadatacenter.http.CedarResponseStatus;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CedarErrorSchemaTest {

  @Test
  void documentedEnumsMatchTheirWireValues() throws Exception {
    assertEquals(errorStatuses(), documentedValues("status"));
    assertEquals(serializedValues(CedarErrorKey.class), documentedValues("errorKey"));
    assertEquals(serializedValues(CedarErrorReasonKey.class), documentedValues("errorReasonKey"));
    assertEquals(serializedValues(CedarErrorType.class), documentedValues("errorType"));
    assertEquals(serializedValues(CedarSuggestedAction.class), documentedValues("suggestedAction"));
  }

  private static Set<String> documentedValues(String fieldName) throws NoSuchFieldException {
    Field field = CedarError.class.getField(fieldName);
    return new LinkedHashSet<>(Arrays.asList(field.getAnnotation(Schema.class).allowableValues()));
  }

  private static Set<String> errorStatuses() {
    Set<String> values = new LinkedHashSet<>();
    for (CedarResponseStatus status : CedarResponseStatus.values()) {
      if (status.getStatusCode() >= 400) {
        values.add(status.name());
      }
    }
    return values;
  }

  private static <E extends Enum<E>> Set<String> serializedValues(Class<E> enumType) throws Exception {
    Method jsonValue = Arrays.stream(enumType.getMethods())
        .filter(method -> method.isAnnotationPresent(com.fasterxml.jackson.annotation.JsonValue.class))
        .findFirst()
        .orElseThrow();
    Set<String> values = new LinkedHashSet<>();
    for (E value : enumType.getEnumConstants()) {
      Object serialized = jsonValue.invoke(value);
      if (serialized != null) {
        values.add(serialized.toString());
      }
    }
    return values;
  }
}
