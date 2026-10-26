package org.metadatacenter.server;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * The creation and modification times a newly stored document states, for the graph node that registers
 * it. The artifact server stamps the document; a node that read the clock again would stand a second
 * ahead of its document whenever the two reads fell either side of a second boundary.
 */
public record ArtifactCreationProvenance(String createdOn, String lastUpdatedOn) {
  public ArtifactCreationProvenance {
    OffsetDateTime.parse(createdOn);
    OffsetDateTime.parse(lastUpdatedOn);
  }

  /**
   * The times the document states, or null when it does not state both as timestamps. The document is
   * already stored when this is read, so a missing or unreadable time leaves the node to stamp its own
   * rather than failing the registration.
   */
  public static ArtifactCreationProvenance fromDocument(JsonNode document) {
    String createdOn = text(document, "pav:createdOn");
    String lastUpdatedOn = text(document, "pav:lastUpdatedOn");
    if (createdOn == null || lastUpdatedOn == null) return null;
    try {
      return new ArtifactCreationProvenance(createdOn, lastUpdatedOn);
    } catch (DateTimeParseException unreadable) {
      return null;
    }
  }

  private static String text(JsonNode document, String property) {
    JsonNode value = document == null ? null : document.get(property);
    return value != null && value.isTextual() ? value.textValue() : null;
  }

  /** Graph models accept the platform's seconds-precision timestamp format. */
  public String graphCreatedOn() {
    return graphTime(createdOn);
  }

  public long createdOnEpochSecond() {
    return OffsetDateTime.parse(createdOn).toEpochSecond();
  }

  public String graphLastUpdatedOn() {
    return graphTime(lastUpdatedOn);
  }

  public long lastUpdatedOnEpochSecond() {
    return OffsetDateTime.parse(lastUpdatedOn).toEpochSecond();
  }

  private static String graphTime(String stated) {
    return org.metadatacenter.constant.CedarConstants.xsdDateTimeFormatter
        .format(OffsetDateTime.parse(stated).toInstant());
  }
}
