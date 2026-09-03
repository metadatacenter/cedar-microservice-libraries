package org.metadatacenter.server.security.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class CedarUserUtilTest {

  @Test
  void generatedApiKeysAreRandom256BitValues() {
    String first = CedarUserUtil.generateApiKey();
    String second = CedarUserUtil.generateApiKey();

    Assertions.assertTrue(first.matches("[0-9a-f]{64}"), first);
    Assertions.assertTrue(second.matches("[0-9a-f]{64}"), second);
    Assertions.assertNotEquals(first, second);
  }
}
