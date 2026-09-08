package org.metadatacenter.util.artifact;

import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.model.ModelNodeNames;

/**
 * Documentation model for a template instance on the wire.
 *
 * <p>An instance is metadata filled in against a template. Its value keys are the template's own
 * field and element names, so beyond the common artifact keys the only fixed key is the IRI of the
 * template it is based on.</p>
 */
@Schema(name = "InstanceArtifactDocument",
    description = "A template instance. Its value keys are the names of the template's fields and "
        + "elements, so the only fixed key beyond the common artifact keys is the template it is "
        + "based on.",
    additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public class InstanceArtifactDocument extends ArtifactDocument {

  @Schema(name = ModelNodeNames.SCHEMA_IS_BASED_ON, description = "IRI of the template the instance "
      + "was filled in against.", format = "uri")
  public String isBasedOn;
}
