package org.metadatacenter.server.valuerecommender;

import org.metadatacenter.server.valuerecommender.model.ValuerecommenderReindexMessage;

public class ValuerecommenderReindexQueue {

  public static ValuerecommenderReindexQueueService valuerecommenderQueueService;

  public static void initQueueService(ValuerecommenderReindexQueueService valuerecommenderQueueService) {
    ValuerecommenderReindexQueue.valuerecommenderQueueService = valuerecommenderQueueService;
  }

  public static void enqueue(ValuerecommenderReindexMessage appLogMessage) {
    valuerecommenderQueueService.enqueueEvent(appLogMessage);
  }

}
