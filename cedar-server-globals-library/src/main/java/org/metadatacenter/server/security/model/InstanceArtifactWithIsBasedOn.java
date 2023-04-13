package org.metadatacenter.server.security.model;

import org.metadatacenter.id.CedarTemplateId;

public interface InstanceArtifactWithIsBasedOn {

  CedarTemplateId getIsBasedOn();

  void setIsBasedOn(CedarTemplateId isBasedOn);
}
