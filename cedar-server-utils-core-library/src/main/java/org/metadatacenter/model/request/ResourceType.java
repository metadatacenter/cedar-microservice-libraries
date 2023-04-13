package org.metadatacenter.model.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import org.metadatacenter.model.CedarResourceType;

public enum ResourceType {

  TEMPLATE(CedarResourceType.Types.TEMPLATE),
  ELEMENT(CedarResourceType.Types.ELEMENT),
  FIELD(CedarResourceType.Types.FIELD),
  INSTANCE(CedarResourceType.Types.INSTANCE);

  private final String value;

  ResourceType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static ResourceType forValue(String value) {
    ResourceType resourceType = null;
    for (ResourceType t : values()) {
      if (t.getValue().equals(value)) {
        resourceType = t;
      }
    }
    return resourceType;
  }
}
