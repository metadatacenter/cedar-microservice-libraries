package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

class CedarEnvironmentClassIsolationExtensionTest {

  @AfterEach
  void restoreEnvironment() {
    CedarEnvironmentSource.clearOverride();
  }

  @Test
  void restoresTheProcessEnvironmentAfterAClass() {
    CedarEnvironmentSource.setOverride(Map.of("CEDAR_TEST_MARKER", "class-local"));

    new CedarEnvironmentClassIsolationExtension().afterAll(null);

    assertFalse(CedarEnvironmentSource.hasOverride());
  }
}
