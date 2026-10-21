package org.metadatacenter.util.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.LinkedDataConfig;
import org.metadatacenter.server.jsonld.LinkedDataUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The admin tool's flat-folder import moves artifacts exported from one deployment into another, so
 * every identifier an artifact gives itself or the artifacts it nests must take the importing
 * deployment's prefix. A term an instance cites belongs to its ontology and must arrive unchanged.
 */
class JsonIdUtilTest {

  private static final String FOREIGN = "https://repo.metadatacenter.org/";
  private static final String LOCAL = "https://repo.metadatacenter.orgx/";
  private static final String CORE = "https://schema.metadatacenter.org/core/";
  private static final String TEMPLATE = "11111111-1111-1111-1111-111111111111";
  private static final String FIELD = "22222222-2222-2222-2222-222222222222";
  private static final String ELEMENT = "33333333-3333-3333-3333-333333333333";
  private static final String NESTED_FIELD = "44444444-4444-4444-4444-444444444444";
  private static final String INSTANCE = "55555555-5555-5555-5555-555555555555";
  private static final String TERM = "http://purl.obolibrary.org/obo/DOID_4";

  private ObjectMapper mapper;
  private LinkedDataUtil linkedDataUtil;

  @BeforeEach
  void setUp() throws Exception {
    mapper = new ObjectMapper();
    linkedDataUtil = new LinkedDataUtil(mapper.readValue(
        "{\"base\":\"" + LOCAL + "\",\"usersBase\":\"" + LOCAL + "users/\"}", LinkedDataConfig.class));
  }

  @Test
  void aTemplateAndEveryArtifactItNestsTakeTheLocalPrefix() throws Exception {
    JsonNode template = mapper.readTree("""
        {"@id": "%1$stemplates/%3$s", "@type": "%2$sTemplate",
         "properties": {
           "f": {"@id": "%1$stemplate-fields/%4$s", "@type": "%2$sTemplateField"},
           "e": {"type": "array", "items": {"@id": "%1$stemplate-elements/%5$s", "@type": "%2$sTemplateElement",
             "properties": {"g": {"@id": "%1$stemplate-fields/%6$s", "@type": "%2$sTemplateField"}}}}}}
        """.formatted(FOREIGN, CORE, TEMPLATE, FIELD, ELEMENT, NESTED_FIELD));

    JsonIdUtil.localizeAtIdsAndTemplateId(template, linkedDataUtil);

    assertEquals(LOCAL + "templates/" + TEMPLATE, template.path("@id").asText());
    assertEquals(LOCAL + "template-fields/" + FIELD, template.at("/properties/f/@id").asText());
    assertEquals(LOCAL + "template-elements/" + ELEMENT, template.at("/properties/e/items/@id").asText());
    assertEquals(LOCAL + "template-fields/" + NESTED_FIELD,
        template.at("/properties/e/items/properties/g/@id").asText());
  }

  @Test
  void anInstanceAndItsTemplateTakeTheLocalPrefixAndTheTermsItCitesDoNot() throws Exception {
    JsonNode instance = mapper.readTree("""
        {"@context": {}, "@id": "%1$stemplate-instances/%2$s", "schema:isBasedOn": "%1$stemplates/%3$s",
         "Disease": {"@id": "%4$s", "rdfs:label": "disease"},
         "Diagnosis": {"@context": {}, "Disease": {"@id": "%4$s", "rdfs:label": "disease"}}}
        """.formatted(FOREIGN, INSTANCE, TEMPLATE, TERM));

    JsonIdUtil.localizeAtIdsAndTemplateId(instance, linkedDataUtil);

    assertEquals(LOCAL + "template-instances/" + INSTANCE, instance.path("@id").asText());
    assertEquals(LOCAL + "templates/" + TEMPLATE, instance.path("schema:isBasedOn").asText());
    assertEquals(TERM, instance.at("/Disease/@id").asText());
    assertEquals(TERM, instance.at("/Diagnosis/Disease/@id").asText());
  }
}
