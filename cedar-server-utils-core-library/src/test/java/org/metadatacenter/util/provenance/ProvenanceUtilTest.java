package org.metadatacenter.util.provenance;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.util.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProvenanceUtilTest {

  private ProvenanceUtil provenanceUtil;
  private ProvenanceInfo provenance;

  @BeforeEach
  void setUp() {
    provenanceUtil = new ProvenanceUtil();
    provenance = new ProvenanceInfo();
    provenance.setCreatedOn("2026-08-11T09:00:00Z");
    provenance.setCreatedBy("https://users.example/caller");
    provenance.setLastUpdatedOn("2026-08-11T09:00:00Z");
    provenance.setLastUpdatedBy("https://users.example/caller");
  }

  @Test
  void anUpdateStampsOnlyTheLastModifiedPair() throws Exception {
    JsonNode artifact = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author",
         "pav:lastUpdatedOn":"2019-01-01T00:00:00Z","oslc:modifiedBy":"https://users.example/author"}
        """);

    provenanceUtil.patchProvenanceInfo(artifact, provenance);

    assertEquals("2019-01-01T00:00:00Z", artifact.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", artifact.get("pav:createdBy").textValue());
    assertEquals("2026-08-11T09:00:00Z", artifact.get("pav:lastUpdatedOn").textValue());
    assertEquals("https://users.example/caller", artifact.get("oslc:modifiedBy").textValue());
  }

  @Test
  void takesCreationProvenanceFromWhatIsStoredRatherThanTheRequest() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2026-08-11T09:00:00Z","pav:createdBy":"https://users.example/impostor"}
        """);
    JsonNode stored = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author"}
        """);

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
  }

  @Test
  void leavesTheRequestValueWhenTheStoredArtifactHasNone() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author"}
        """);
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"schema:name\":\"no provenance recorded\"}");

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
  }

  @Test
  void treatsAStoredNullAsNoValueRecorded() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author"}
        """);
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"pav:createdOn\":null,\"pav:createdBy\":null}");

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
  }

  @Test
  void addsCreationProvenanceWhenTheRequestOmitsItAndTheStoredArtifactHasIt() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("{\"schema:name\":\"repaired\"}");
    JsonNode stored = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author"}
        """);

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
  }

  @Test
  void ignoresAMissingStoredArtifactRatherThanFailing() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("{\"pav:createdBy\":\"https://users.example/author\"}");

    provenanceUtil.preserveCreationProvenance(request, null);

    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
  }

  // ── an untouched child ───────────────────────────────────────────────────

  @Test
  void anUntouchedChildTakesEveryProvenanceValueTheStoredArtifactRecords() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2026-08-11T09:00:00Z","pav:createdBy":"https://users.example/impostor",
         "pav:lastUpdatedOn":"2026-08-11T09:00:00Z","oslc:modifiedBy":"https://users.example/impostor"}
        """);
    JsonNode stored = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author",
         "pav:lastUpdatedOn":"2020-06-01T00:00:00Z","oslc:modifiedBy":"https://users.example/editor"}
        """);

    provenanceUtil.copyProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue());
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
    assertEquals("2020-06-01T00:00:00Z", request.get("pav:lastUpdatedOn").textValue());
    assertEquals("https://users.example/editor", request.get("oslc:modifiedBy").textValue());
  }

  @Test
  void anUntouchedChildKeepsAValueTheStoredArtifactDoesNotRecord() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("""
        {"pav:createdOn":"2019-01-01T00:00:00Z","pav:createdBy":"https://users.example/author",
         "pav:lastUpdatedOn":"2020-06-01T00:00:00Z","oslc:modifiedBy":"https://users.example/editor"}
        """);
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"pav:lastUpdatedOn\":\"2020-06-01T00:00:00Z\"}");

    provenanceUtil.copyProvenance(request, stored);

    assertEquals("2019-01-01T00:00:00Z", request.get("pav:createdOn").textValue(),
        "a child missing its provenance must be repairable by supplying it");
    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue());
    assertEquals("https://users.example/editor", request.get("oslc:modifiedBy").textValue());
    assertEquals("2020-06-01T00:00:00Z", request.get("pav:lastUpdatedOn").textValue(),
        "the one value the stored artifact does record still wins");
  }

  @Test
  void anUntouchedChildKeepsAValueTheStoredArtifactRecordsAsNull() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree(
        "{\"pav:createdBy\":\"https://users.example/author\"}");
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"pav:createdBy\":null}");

    provenanceUtil.copyProvenance(request, stored);

    assertEquals("https://users.example/author", request.get("pav:createdBy").textValue(),
        "a stored null records nothing, so it must not overwrite a supplied value");
  }

  @Test
  void ignoresATargetThatIsNotAnObject() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("[]");
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"pav:createdBy\":\"https://users.example/author\"}");

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertTrue(request.isArray());
    assertNull(request.get("pav:createdBy"));
  }
}
