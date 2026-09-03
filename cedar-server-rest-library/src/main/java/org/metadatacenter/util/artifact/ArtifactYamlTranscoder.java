package org.metadatacenter.util.artifact;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.metadatacenter.artifacts.model.core.Artifact;
import org.metadatacenter.artifacts.model.core.TemplateInstanceArtifact;
import org.metadatacenter.artifacts.model.core.TemplateSchemaArtifact;
import org.metadatacenter.artifacts.model.reader.JsonArtifactReader;
import org.metadatacenter.artifacts.model.reader.YamlArtifactReader;
import org.metadatacenter.artifacts.model.renderer.JsonArtifactRenderer;
import org.metadatacenter.artifacts.model.tools.InstanceInflater;
import org.metadatacenter.artifacts.model.tools.YamlSerializer;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.util.json.JsonMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

/**
 * Converts artifacts between their stored JSON Schema/JSON-LD form and the YAML serialization
 * provided by the cedar-artifact-library. The storage layer remains JSON-only; YAML exists purely
 * as a request/response representation produced on the fly.
 *
 * Two YAML media types are recognized: application/x-yaml, the value of
 * HttpConstants.CONTENT_TYPE_APPLICATION_YAML used across CEDAR, and application/yaml, the type
 * registered by RFC 9512.
 */
public final class ArtifactYamlTranscoder {

  public static final MediaType APPLICATION_X_YAML_TYPE = new MediaType("application", "x-yaml");
  public static final MediaType APPLICATION_YAML_TYPE = new MediaType("application", "yaml");

  private static final List<MediaType> YAML_TYPES = List.of(APPLICATION_X_YAML_TYPE, APPLICATION_YAML_TYPE);

  /**
   * The keys the system records about a stored artifact. The compact YAML form strips all of
   * them while keeping the id; the minimal authoring form carries none of them and no id
   * either. An id with none of these present is therefore the signature of compact input.
   */
  private static final List<String> SYSTEM_RECORDED_KEYS =
      List.of("modelVersion", "version", "status", "createdOn", "createdBy", "modifiedOn", "modifiedBy");

  /**
   * Supplies the stored template an instance is based on, so a YAML instance can be completed
   * against it. The two servers reach a template differently — the artifact server reads its own
   * store, the resource server asks the artifact server — so each passes its own.
   */
  @FunctionalInterface
  public interface TemplateResolver {
    /** The template's JSON, or null when this server holds no template under that IRI. */
    JsonNode templateFor(String templateIri) throws IOException;
  }

  private static final ObjectMapper YAML_MAPPER = new ObjectMapper(new YAMLFactory());

  private static final Logger log = LoggerFactory.getLogger(ArtifactYamlTranscoder.class);

  private ArtifactYamlTranscoder() {
  }

  /**
   * Thrown when a YAML request body is the compact form, which is a lossy read-time convenience
   * and can not be stored.
   */
  public static final class CompactYamlBodyException extends RuntimeException {
    private CompactYamlBodyException(String message) {
      super(message);
    }
  }

  /**
   * Selects the response media type from the acceptable media types of a request. The list is
   * expected in preference order, as returned by HttpHeaders.getAcceptableMediaTypes(). JSON is
   * the default: it matches wildcards, and an absent Accept header yields a wildcard entry. When
   * the client asks for YAML, the returned type is the concrete YAML type it asked for, so the
   * response can echo it. An empty Optional means the client asked only for types the server can
   * not produce.
   */
  public static Optional<MediaType> negotiateResponseType(List<MediaType> acceptableMediaTypes) {
    if (acceptableMediaTypes == null || acceptableMediaTypes.isEmpty()) {
      return Optional.of(MediaType.APPLICATION_JSON_TYPE);
    }
    for (MediaType requested : acceptableMediaTypes) {
      if (requested.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
        return Optional.of(MediaType.APPLICATION_JSON_TYPE);
      }
      for (MediaType yamlType : YAML_TYPES) {
        if (requested.isCompatible(yamlType)) {
          return Optional.of(yamlType);
        }
      }
    }
    return Optional.empty();
  }

  public static boolean isJson(MediaType mediaType) {
    return MediaType.APPLICATION_JSON_TYPE.equals(mediaType);
  }

  /**
   * Decides whether a request body is YAML based on its Content-Type. Parameters such as charset
   * are ignored; wildcards do not match.
   */
  public static boolean isYaml(MediaType contentType) {
    if (contentType == null) {
      return false;
    }
    for (MediaType yamlType : YAML_TYPES) {
      if (yamlType.getType().equalsIgnoreCase(contentType.getType())
          && yamlType.getSubtype().equalsIgnoreCase(contentType.getSubtype())) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts a YAML artifact serialization into the JSON Schema/JSON-LD form expected by the
   * artifact server. The YAML is parsed, read into the artifact model, and rendered as JSON.
   *
   * <p>The compact form is rejected: it carries the artifact's id but strips the system-recorded
   * keys, so storing it would silently regenerate that content. The minimal authoring form — no id,
   * the system supplies the rest — and the full form both pass through. Asking to write compact
   * explicitly, with {@code ?compact=true} on a POST or PUT, is also refused by the resources.
   */
  public static String yamlToJsonString(String yamlContent, CedarResourceType resourceType) throws IOException {
    return yamlToJsonString(yamlContent, resourceType, null);
  }

  /**
   * As above, completing an instance against the template {@code templateResolver} supplies.
   *
   * <p>A YAML instance carries only the fields that hold a value: the serialization has no way to
   * write an empty one, refusing an empty mapping and a null alike, and the model regards an
   * instance that omits them as whole. The JSON it becomes here may not — a template's schema marks
   * every one of its properties required — so the missing slots are materialized on the way through.
   * The requirement is met where it arises, at the boundary that produces the JSON, rather than by
   * asking every YAML author to write something their serialization cannot express.
   *
   * <p>Only a YAML body passes through here, so a JSON client is unaffected: a JSON instance is
   * stored as it was sent, and a field it omits is still refused. The two cases are the same
   * document and can only be told apart by the serialization it arrived in — omission means "empty"
   * in YAML and "gone" in JSON.
   *
   * <p>A null resolver skips the completion, which is what every non-instance kind passes.
   */
  public static String yamlToJsonString(String yamlContent, CedarResourceType resourceType,
                                        TemplateResolver templateResolver) throws IOException {
    LinkedHashMap<String, Object> yamlMap =
        YAML_MAPPER.readValue(yamlContent, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    if (yamlMap.get("id") != null && SYSTEM_RECORDED_KEYS.stream().noneMatch(yamlMap::containsKey)) {
      throw new CompactYamlBodyException("The YAML body appears to be the compact form: it carries an id but none "
          + "of the system-recorded keys (version, status, modelVersion, provenance). The compact form is a lossy "
          + "read-time convenience and can not be stored. Submit the full form, or omit the id to author minimally. "
          + "See https://metadatacenter.readthedocs.io/en/latest/yaml-spec/minimal-and-full/");
    }
    // Which reader the body asks for. Once compact input has been rejected above, a document naming
    // the artifact it describes is the full form; a document naming none is being authored, so the
    // compact-capable reader tolerates the absent model version and defaults it for the minimal form.
    YamlArtifactReader reader = new YamlArtifactReader(yamlMap.get("id") == null);
    JsonArtifactRenderer renderer = new JsonArtifactRenderer();
    ObjectNode rendered = switch (resourceType) {
      case TEMPLATE -> renderer.renderTemplateSchemaArtifact(reader.readTemplateSchemaArtifact(yamlMap));
      case ELEMENT -> renderer.renderElementSchemaArtifact(reader.readElementSchemaArtifact(yamlMap));
      case FIELD -> renderer.renderFieldSchemaArtifact(reader.readFieldSchemaArtifact(yamlMap));
      case INSTANCE -> renderer.renderTemplateInstanceArtifact(
          completed(reader.readTemplateInstanceArtifact(yamlMap), templateResolver));
      default -> throw new IllegalArgumentException("YAML is not supported for resource type: " + resourceType);
    };
    return JsonMapper.MAPPER.writeValueAsString(rendered);
  }

  private static TemplateInstanceArtifact completed(TemplateInstanceArtifact instance,
                                                    TemplateResolver templateResolver) throws IOException {
    if (templateResolver == null) {
      return instance;
    }
    String templateIri = instance.isBasedOn().toString();
    JsonNode template = templateResolver.templateFor(templateIri);
    if (template == null) {
      throw new IllegalArgumentException(
          "the template this instance says it isBasedOn can not be found: " + templateIri);
    }
    TemplateSchemaArtifact schema = new JsonArtifactReader().readTemplateSchemaArtifact((ObjectNode) template);
    return InstanceInflater.inflate(schema, instance);
  }

  /**
   * Applies the negotiated response type to a response a resource built as JSON. When the client
   * asked for YAML and the entity is the artifact's JSON, the entity is re-rendered as YAML.
   * Everything else keeps the JSON it was built as, and says so.
   *
   * <p>Saying so is the point. What is left unrendered is not an artifact — an error, or the folder
   * record a write answers with — and neither has a YAML form. A response that names no media type
   * is written in the one the Accept header negotiated, so leaving it unnamed asked Jersey for YAML
   * it has no writer for, and a successful write came back 500.
   *
   * @param jsonResponse the response as the resource built it, entity and status included
   * @param resourceType the artifact kind, for reading the entity into the model
   * @param responseType the negotiated type, from {@link #negotiateResponseType}; empty means the
   *                     client asked for nothing this server can produce, and the response is left
   *                     to the caller to refuse
   */
  public static Response negotiatedArtifactResponse(Response jsonResponse, CedarResourceType resourceType,
                                                    Optional<MediaType> responseType) {
    if (responseType.isEmpty() || isJson(responseType.get())) {
      return jsonResponse;
    }
    if (Response.Status.Family.familyOf(jsonResponse.getStatus()) != Response.Status.Family.SUCCESSFUL
        || !(jsonResponse.getEntity() instanceof JsonNode artifactNode)) {
      return asJson(jsonResponse);
    }
    try {
      return Response.fromResponse(jsonResponse)
          .entity(jsonToYaml(artifactNode, resourceType, false))
          .type(responseType.get())
          .build();
    } catch (Exception e) {
      log.warn("The artifact could not be rendered as YAML; returning the JSON response", e);
      return asJson(jsonResponse);
    }
  }

  private static Response asJson(Response response) {
    return Response.fromResponse(response).type(MediaType.APPLICATION_JSON_TYPE).build();
  }

  /**
   * Converts an artifact's JSON Schema/JSON-LD form into its YAML serialization.
   */
  public static String jsonToYaml(JsonNode artifactNode, CedarResourceType resourceType, boolean compact) {
    Artifact artifact = readJsonArtifact((ObjectNode) artifactNode, resourceType);
    return YamlSerializer.getYAML(artifact, compact, true);
  }

  private static Artifact readJsonArtifact(ObjectNode artifactNode, CedarResourceType resourceType) {
    JsonArtifactReader reader = new JsonArtifactReader();
    return switch (resourceType) {
      case TEMPLATE -> reader.readTemplateSchemaArtifact(artifactNode);
      case ELEMENT -> reader.readElementSchemaArtifact(artifactNode);
      case FIELD -> reader.readFieldSchemaArtifact(artifactNode);
      case INSTANCE -> reader.readTemplateInstanceArtifact(artifactNode);
      default -> throw new IllegalArgumentException("YAML is not supported for resource type: " + resourceType);
    };
  }

}
