package org.metadatacenter.model;

import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarResourceId;

public interface CedarResource<T extends CedarResourceId> {

  T getResourceId() throws CedarProcessingException;
}
