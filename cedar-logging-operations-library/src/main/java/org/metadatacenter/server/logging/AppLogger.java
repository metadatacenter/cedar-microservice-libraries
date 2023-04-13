package org.metadatacenter.server.logging;

import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

public class AppLogger {

  public static AppLoggerQueueService appLoggerQueueService;
  private static SystemComponent systemComponent;

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
    appLoggerQueueService.enqueueEvent(appLogMessage);
  }

}
