package org.metadatacenter.util.test;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CedarTestBaselineTest {

  private static final Map<String, String> PROFILE = Map.of(
      "CEDAR_HOME", "/Users/someone/CEDAR",
      "CEDAR_HOST", "metadatacenter.orgx",
      "CEDAR_MONGO_HOST", "localhost",
      "CEDAR_MONGO_PORT", "27017",
      "CEDAR_NEO4J_BOLT_PORT", "7687",
      "CEDAR_ARTIFACT_SERVER_HOST", "127.0.0.1",
      "CEDAR_ARTIFACT_HTTP_PORT", "9001",
      "CEDAR_ARTIFACT_ADMIN_PORT", "9101",
      "CEDAR_MONGO_APP_USER_PASSWORD", "secret",
      "PATH", "/usr/bin");

  @Test
  void aStoreAddressPointsWhereNothingListens() {
    Map<String, String> baseline = CedarTestBaseline.from(PROFILE);

    assertEquals("127.0.0.1", baseline.get("CEDAR_MONGO_HOST"));
    assertEquals("1", baseline.get("CEDAR_MONGO_PORT"));
    assertEquals("1", baseline.get("CEDAR_NEO4J_BOLT_PORT"));
  }

  @Test
  void aServerPortIsOneTheOperatingSystemAssigns() {
    Map<String, String> baseline = CedarTestBaseline.from(PROFILE);

    assertEquals("127.0.0.1", baseline.get("CEDAR_ARTIFACT_SERVER_HOST"));
    assertEquals("0", baseline.get("CEDAR_ARTIFACT_HTTP_PORT"));
    assertEquals("0", baseline.get("CEDAR_ARTIFACT_ADMIN_PORT"));
  }

  @Test
  void everythingThatIsNotAnAddressIsKept() {
    Map<String, String> baseline = CedarTestBaseline.from(PROFILE);

    assertEquals("metadatacenter.orgx", baseline.get("CEDAR_HOST"), "the deployment domain is not a service address");
    assertEquals("/Users/someone/CEDAR", baseline.get("CEDAR_HOME"));
    assertEquals("secret", baseline.get("CEDAR_MONGO_APP_USER_PASSWORD"));
    assertEquals("/usr/bin", baseline.get("PATH"));
    assertEquals(PROFILE.keySet(), baseline.keySet());
  }
}
