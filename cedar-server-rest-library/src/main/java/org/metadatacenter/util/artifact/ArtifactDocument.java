package org.metadatacenter.util.artifact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.ModelNodeNames;

import java.util.Map;

/**
 * Documentation model for the surface every CEDAR artifact shares on the wire.
 *
 * <p>Artifacts are open JSON-LD documents. Templates, elements, fields and instances carry different
 * model-defined properties, and a template's own field names are legal top-level keys, so this
 * class describes the identity and provenance keys they all have in common and leaves the object
 * open. The two subclasses add what a schema artifact or an instance carries beyond that. The full
 * definition of each kind is the JSON Schema in cedar-model-validation-library, which every write
 * is validated against.</p>
 *
 * <p>The class has no runtime role: resource methods read and write artifacts as raw JSON or
 * YAML. It exists so that the four services that serve artifacts describe them the same way, the
 * way they already share {@link org.metadatacenter.util.http.CedarError}.</p>
 */
@Schema(name = "ArtifactDocument",
    description = "An open CEDAR JSON-LD artifact: the identity and provenance keys that templates, "
        + "elements, fields and instances all carry. Kind-specific keys are described by "
        + "SchemaArtifactDocument and InstanceArtifactDocument; the authoritative definition of each "
        + "kind is the CEDAR model's JSON Schema.",
    externalDocs = @io.swagger.v3.oas.annotations.ExternalDocumentation(
        description = "The CEDAR template model",
        url = "https://more.metadatacenter.org/tools-training/outreach/cedar-template-model"),
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public class ArtifactDocument {

  @Schema(name = ModelNodeNames.JSON_LD_ID, description = "The artifact's IRI. The server mints it on a "
      + "POST, so a body sent to POST carries none; a body sent to PUT carries the IRI of the path.",
      format = "uri")
  public String id;

  @Schema(name = ModelNodeNames.JSON_LD_CONTEXT, description = "JSON-LD context for the keys the "
      + "artifact uses.", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  public Map<String, Object> context;

  @Schema(name = ModelNodeNames.SCHEMA_ORG_NAME, description = "Display name.")
  public String name;

  @Schema(name = ModelNodeNames.SCHEMA_ORG_DESCRIPTION, description = "Description shown with the name.")
  public String description;

  @Schema(name = ModelNodeNames.PAV_CREATED_ON, description = "When the artifact was created.",
      format = "date-time")
  public String createdOn;

  @Schema(name = ModelNodeNames.PAV_CREATED_BY, description = "IRI of the user who created it.",
      format = "uri")
  public String createdBy;

  @Schema(name = ModelNodeNames.PAV_LAST_UPDATED_ON, description = "When the artifact was last changed.",
      format = "date-time")
  public String lastUpdatedOn;

  @Schema(name = ModelNodeNames.OSLC_MODIFIED_BY, description = "IRI of the user who last changed it.",
      format = "uri")
  public String modifiedBy;
}
