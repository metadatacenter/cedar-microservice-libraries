package org.metadatacenter.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;

/** Modification metadata stated by a verbatim document, independently of the repair's actor/time. */
public record ArtifactModificationProvenance(String modifiedBy, String modifiedOn) {
  public ArtifactModificationProvenance {
    if (modifiedOn != null) OffsetDateTime.parse(modifiedOn);
  }

  public static ArtifactModificationProvenance fromDocument(JsonNode document) {
    return new ArtifactModificationProvenance(textOrNull(document, "oslc:modifiedBy"),
        textOrNull(document, "pav:lastUpdatedOn"));
  }

  private static String textOrNull(JsonNode document, String property) {
    JsonNode value = document.get(property);
    if (value == null || value.isNull()) return null;
    if (!value.isTextual()) throw new IllegalArgumentException(property + " must be a string or null");
    return value.textValue();
  }

  /** Graph models accept the platform's seconds-precision timestamp format. */
  public String graphModifiedOn() {
    return modifiedOn == null ? null : org.metadatacenter.constant.CedarConstants.xsdDateTimeFormatter
        .format(OffsetDateTime.parse(modifiedOn).toInstant());
  }

  public Long epochSecond() {
    return modifiedOn == null ? null : OffsetDateTime.parse(modifiedOn).toEpochSecond();
  }
}
