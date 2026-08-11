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

  @Test
  void ignoresATargetThatIsNotAnObject() throws Exception {
    JsonNode request = JsonMapper.MAPPER.readTree("[]");
    JsonNode stored = JsonMapper.MAPPER.readTree("{\"pav:createdBy\":\"https://users.example/author\"}");

    provenanceUtil.preserveCreationProvenance(request, stored);

    assertTrue(request.isArray());
    assertNull(request.get("pav:createdBy"));
  }
}
