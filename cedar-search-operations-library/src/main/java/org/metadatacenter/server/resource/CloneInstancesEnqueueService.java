package org.metadatacenter.server.resource;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.server.queue.util.CloneInstancesQueueService;

public class CloneInstancesEnqueueService {

  private final CloneInstancesQueueService queueService;

  public CloneInstancesEnqueueService(CedarConfig cedarConfig) {
    this.queueService = new CloneInstancesQueueService(cedarConfig.getCacheConfig().getPersistent());
  }

  private void enqueue(CedarTemplateId oldId, CedarTemplateId newId) {
    CloneInstancesQueueEvent event = new CloneInstancesQueueEvent(oldId, newId);
    queueService.enqueueEvent(event);
  }

  public void cloneInstances(CedarTemplateId oldId, CedarTemplateId newId) {
    enqueue(oldId, newId);
  }

}
