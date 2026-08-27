package org.metadatacenter.server.logging;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.logging.model.AppLogMessage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppLoggerTest {

  @Test
  void mavenTestProcessSuppressesQueueWrites() {
    assertTrue(Boolean.getBoolean(AppLogger.TEST_SUPPRESSION_PROPERTY));
    AppLogger.appLoggerQueueService = null;

    assertDoesNotThrow(() -> AppLogger.enqueue(new AppLogMessage()));
  }
}
