package org.metadatacenter.util.artifact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.ModelNodeNames;

import java.util.List;
import java.util.Map;

/**
 * Documentation model for a template, template element or template field on the wire.
 *
 * <p>A schema artifact is a JSON Schema document as well as a JSON-LD one: it declares the
 * properties an instance may carry and how the editor renders them. This class names the
 * versioning and JSON Schema keys the model requires on top of {@link ArtifactDocument}; the
 * entries under {@code properties} depend on the template and are not described here.</p>
 */
@Schema(name = "SchemaArtifactDocument",
    description = "A template, template element or template field. Beyond the common artifact "
        + "keys it carries the model's versioning keys and the JSON Schema keys that declare the "
        + "properties an instance may hold. The entries under `properties` depend on the artifact.",
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public class SchemaArtifactDocument extends ArtifactDocument {

  @Schema(name = ModelNodeNames.JSON_LD_TYPE, description = "IRI of the artifact kind.", format = "uri",
      allowableValues = {ModelNodeNames.TEMPLATE_SCHEMA_ARTIFACT_TYPE_IRI,
          ModelNodeNames.ELEMENT_SCHEMA_ARTIFACT_TYPE_IRI, ModelNodeNames.FIELD_SCHEMA_ARTIFACT_TYPE_IRI,
          ModelNodeNames.STATIC_FIELD_SCHEMA_ARTIFACT_TYPE_IRI})
  public String type;

  @Schema(name = ModelNodeNames.SCHEMA_ORG_SCHEMA_VERSION, description = "Version of the CEDAR model the "
      + "artifact conforms to.")
  public String schemaVersion;

  @Schema(name = ModelNodeNames.PAV_VERSION, description = "The artifact's own version.")
  public String version;

  @Schema(name = ModelNodeNames.BIBO_STATUS, description = "Publication status. A published artifact "
      + "can no longer be changed.", allowableValues = {"bibo:draft", "bibo:published"})
  public String status;

  @Schema(name = ModelNodeNames.PAV_PREVIOUS_VERSION, description = "IRI of the version this one was "
      + "drafted from, when there is one.", format = "uri", nullable = true)
  public String previousVersion;

  @Schema(name = ModelNodeNames.PAV_DERIVED_FROM, description = "IRI of the artifact this one was copied "
      + "from, when there is one.", format = "uri", nullable = true)
  public String derivedFrom;

  @Schema(name = ModelNodeNames.SCHEMA_ORG_IDENTIFIER, description = "Caller-assigned identifier.",
      nullable = true)
  public String identifier;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_SCHEMA, description = "The JSON Schema dialect IRI.",
      format = "uri")
  public String schema;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_TYPE, description = "Always `object`.")
  public String jsonSchemaType;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_TITLE, description = "JSON Schema title, derived from the name.")
  public String title;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_DESCRIPTION, description = "JSON Schema description, derived "
      + "from the name.")
  public String jsonSchemaDescription;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_PROPERTIES, description = "One entry per child field or "
      + "element, keyed by its name, plus the JSON-LD keys an instance must carry.",
      additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  public Map<String, Object> properties;

  @Schema(name = ModelNodeNames.JSON_SCHEMA_REQUIRED, description = "Names of the properties an instance "
      + "must carry.")
  public List<String> required;

  @Schema(name = ModelNodeNames.UI, description = "Editor rendering directives: child order, labels, "
      + "descriptions and, for a field, its input type.",
      additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
  public Map<String, Object> ui;
}
