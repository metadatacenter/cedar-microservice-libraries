package org.metadatacenter.util.artifact;

import org.junit.jupiter.api.Test;

import org.metadatacenter.artifacts.model.core.TemplateSchemaArtifact;
import org.metadatacenter.artifacts.model.renderer.JsonArtifactRenderer;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.util.json.JsonMapper;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Accept-header and Content-Type decisions of ArtifactYamlTranscoder. The media type
 * lists mirror what HttpHeaders.getAcceptableMediaTypes() produces: sorted by preference, with a
 * wildcard entry when the Accept header is absent.
 */
public class ArtifactYamlTranscoderNegotiationTest {

  private static final MediaType X_YAML = ArtifactYamlTranscoder.APPLICATION_X_YAML_TYPE;
  private static final MediaType YAML = ArtifactYamlTranscoder.APPLICATION_YAML_TYPE;
  private static final MediaType JSON = MediaType.APPLICATION_JSON_TYPE;

  private Optional<MediaType> negotiate(List<MediaType> acceptable) {
    return ArtifactYamlTranscoder.negotiateResponseType(acceptable);
  }

  @Test
  public void absentAcceptHeaderYieldsJson() {
    assertEquals(Optional.of(JSON), negotiate(null));
    assertEquals(Optional.of(JSON), negotiate(Collections.emptyList()));
  }

  @Test
  public void jsonYieldsJson() {
    assertEquals(Optional.of(JSON), negotiate(asList(JSON)));
  }

  @Test
  public void eitherYamlTypeYieldsTheTypeTheClientAskedFor() {
    assertEquals(Optional.of(X_YAML), negotiate(asList(X_YAML)));
    assertEquals(Optional.of(YAML), negotiate(asList(YAML)));
  }

  @Test
  public void wildcardYieldsJson() {
    assertEquals(Optional.of(JSON), negotiate(asList(MediaType.WILDCARD_TYPE)));
  }

  @Test
  public void yamlPreferredOverJsonWhenListedFirst() {
    assertEquals(Optional.of(YAML), negotiate(asList(YAML, JSON)));
    assertEquals(Optional.of(X_YAML), negotiate(asList(X_YAML, JSON)));
  }

  @Test
  public void jsonPreferredOverYamlWhenListedFirst() {
    assertEquals(Optional.of(JSON), negotiate(asList(JSON, YAML)));
  }

  @Test
  public void unsupportedTypeAloneIsNotAcceptable() {
    assertEquals(Optional.empty(), negotiate(asList(MediaType.TEXT_HTML_TYPE)));
  }

  @Test
  public void unsupportedTypeFollowedByWildcardYieldsJson() {
    // The browser pattern: Accept: text/html, */*;q=0.8
    assertEquals(Optional.of(JSON), negotiate(asList(MediaType.TEXT_HTML_TYPE, MediaType.WILDCARD_TYPE)));
  }

  @Test
  public void unsupportedTypeFollowedByYamlYieldsYaml() {
    assertEquals(Optional.of(YAML), negotiate(asList(MediaType.TEXT_HTML_TYPE, YAML)));
  }

  @Test
  public void anInstanceAlsoNegotiatesNQuads() {
    MediaType nquads = ArtifactYamlTranscoder.APPLICATION_NQUADS_TYPE;
    assertEquals(Optional.of(nquads), ArtifactYamlTranscoder.negotiateInstanceResponseType(asList(nquads)));
    assertEquals(Optional.of(nquads), ArtifactYamlTranscoder.negotiateInstanceResponseType(asList(nquads, JSON)));
    assertEquals(Optional.of(JSON), ArtifactYamlTranscoder.negotiateInstanceResponseType(asList(JSON, nquads)));
    assertEquals(Optional.of(JSON), ArtifactYamlTranscoder.negotiateInstanceResponseType(asList(MediaType.WILDCARD_TYPE)));
    assertEquals(Optional.empty(), negotiate(asList(nquads)), "only an instance has an RDF form");
  }

  @Test
  public void jsonIsAcceptedWhereverTheAcceptHeaderAdmitsIt() {
    assertTrue(ArtifactYamlTranscoder.acceptsJson(null));
    assertTrue(ArtifactYamlTranscoder.acceptsJson(Collections.emptyList()));
    assertTrue(ArtifactYamlTranscoder.acceptsJson(asList(YAML, JSON)));
    assertTrue(ArtifactYamlTranscoder.acceptsJson(asList(YAML, MediaType.valueOf("application/*"))));
    assertTrue(ArtifactYamlTranscoder.acceptsJson(asList(YAML, MediaType.WILDCARD_TYPE)));
    assertFalse(ArtifactYamlTranscoder.acceptsJson(asList(YAML)));
    assertFalse(ArtifactYamlTranscoder.acceptsJson(asList(YAML, X_YAML)));
  }

  @Test
  public void anArtifactTheLibraryCanNotReadHasNoYamlForm() {
    com.fasterxml.jackson.databind.node.ObjectNode template =
        (com.fasterxml.jackson.databind.node.ObjectNode) templateJson();
    template.put("pav:createdOn", "yesterday");

    for (com.fasterxml.jackson.databind.JsonNode unreadable :
        List.of(template, JsonMapper.STRICT_MAPPER.createArrayNode(), JsonMapper.STRICT_MAPPER.createObjectNode())) {
      ArtifactYamlTranscoder.UnreadableArtifactException e = org.junit.jupiter.api.Assertions.assertThrows(
          ArtifactYamlTranscoder.UnreadableArtifactException.class,
          () -> ArtifactYamlTranscoder.jsonToYaml(unreadable, CedarResourceType.TEMPLATE, false));
      Response refusal = ArtifactYamlTranscoder.noYamlFormResponse("https://repo.metadatacenter.org/templates/1",
          CedarResourceType.TEMPLATE, e);
      assertEquals(406, refusal.getStatus());
      assertEquals(JSON, refusal.getMediaType());
    }
  }

  @Test
  public void isYamlMatchesBothYamlMediaTypes() {
    assertTrue(ArtifactYamlTranscoder.isYaml(new MediaType("application", "x-yaml")));
    assertTrue(ArtifactYamlTranscoder.isYaml(new MediaType("application", "yaml")));
    assertTrue(ArtifactYamlTranscoder.isYaml(MediaType.valueOf("application/yaml; charset=utf-8")));
  }

  @Test
  public void isYamlRejectsOtherAndWildcardTypes() {
    assertFalse(ArtifactYamlTranscoder.isYaml(null));
    assertFalse(ArtifactYamlTranscoder.isYaml(JSON));
    assertFalse(ArtifactYamlTranscoder.isYaml(MediaType.WILDCARD_TYPE));
    assertFalse(ArtifactYamlTranscoder.isYaml(new MediaType("text", "yaml")));
  }

  @Test
  public void isJsonMatchesOnlyJson() {
    assertTrue(ArtifactYamlTranscoder.isJson(JSON));
    assertFalse(ArtifactYamlTranscoder.isJson(YAML));
    assertFalse(ArtifactYamlTranscoder.isJson(null));
  }


  // Applying the negotiated type to a response

  /** A stored template, the shape a write response carries when it carries the artifact. */
  private static com.fasterxml.jackson.databind.JsonNode templateJson() {
    return new JsonArtifactRenderer().renderTemplateSchemaArtifact(
        TemplateSchemaArtifact.builder()
            .withName("Study")
            .withJsonLdId(java.net.URI.create("https://repo.metadatacenter.org/templates/1"))
            .build());
  }

  @Test
  public void anArtifactIsRenderedAsYamlWhenYamlWasNegotiated() {
    Response built = Response.ok().entity(templateJson()).build();

    Response negotiated = ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.of(YAML));

    assertEquals(YAML, negotiated.getMediaType());
    assertTrue(String.valueOf(negotiated.getEntity()).startsWith("type: template"),
        "the entity should be the artifact's YAML, got: " + negotiated.getEntity());
  }

  @Test
  public void anEntityThatIsNotTheArtifactKeepsItsJsonAndSaysSo() {
    // What a successful PUT answers with: the artifact's folder record, which has no YAML form.
    // Leaving the type unset let Jersey write it as the negotiated YAML, which it cannot, so the
    // write came back 500.
    Response built = Response.ok().entity(new FolderRecord("template")).build();

    Response negotiated = ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.of(YAML));

    assertEquals(JSON, negotiated.getMediaType());
    assertEquals(200, negotiated.getStatus());
    assertTrue(negotiated.getEntity() instanceof FolderRecord, "the entity is passed through");
  }

  @Test
  public void anErrorKeepsItsJsonAndItsStatus() {
    Response built = Response.status(404).entity(JsonMapper.STRICT_MAPPER.createObjectNode()
        .put("errorKey", "artifactNotFound")).build();

    Response negotiated = ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.of(YAML));

    assertEquals(JSON, negotiated.getMediaType());
    assertEquals(404, negotiated.getStatus());
  }

  @Test
  public void anArtifactTheModelCanNotReadFallsBackToJson() {
    Response built = Response.ok().entity(JsonMapper.STRICT_MAPPER.createObjectNode().put("not", "an artifact")).build();

    Response negotiated = ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.of(YAML));

    assertEquals(JSON, negotiated.getMediaType());
    assertEquals(200, negotiated.getStatus());
  }

  @Test
  public void aJsonNegotiationLeavesTheResponseAlone() {
    Response built = Response.ok().entity(templateJson()).build();

    assertEquals(built, ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.of(JSON)));
    assertEquals(built, ArtifactYamlTranscoder.negotiatedArtifactResponse(
        built, CedarResourceType.TEMPLATE, Optional.empty()));
  }

  /** Stands in for the folder record a write answers with: a bean, not the artifact's JSON. */
  private record FolderRecord(String resourceType) {}

}
