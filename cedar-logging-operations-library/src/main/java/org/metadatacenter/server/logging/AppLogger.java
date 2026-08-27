package org.metadatacenter.server.logging;

import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

public class AppLogger {

  public static AppLoggerQueueService appLoggerQueueService;
  private static SystemComponent systemComponent;
  static final String TEST_SUPPRESSION_PROPERTY = "cedar.test.suppressAppLogQueue";

  public static void initLoggerQueueService(AppLoggerQueueService appLoggerQueueService,
                                            SystemComponent systemComponent) {
    AppLogger.appLoggerQueueService = appLoggerQueueService;
    AppLogger.systemComponent = systemComponent;
  }

  public static AppLogMessage message(AppLogType type, AppLogSubType subType, String globalRequestId,
                                      String localRequestId) {
    AppLogMessage m = new AppLogMessage(systemComponent, type, subType, globalRequestId, localRequestId);
    return m;
  }

  public static void enqueue(AppLogMessage appLogMessage) {
    // Backend-free Maven suites set this property in cedar-parent. Queue delivery and outage
    // behavior have dedicated embedded-Redis tests; the other suites should not pay a network
    // timeout for every request merely to prove that their intentionally absent Redis is absent.
    if (!Boolean.getBoolean(TEST_SUPPRESSION_PROPERTY)) {
      appLoggerQueueService.enqueueEvent(appLogMessage);
    }
  }

}
