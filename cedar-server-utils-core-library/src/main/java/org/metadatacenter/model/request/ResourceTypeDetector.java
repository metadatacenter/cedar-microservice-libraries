package org.metadatacenter.model.request;

import com.google.common.base.Strings;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.error.CedarErrorType;
import org.metadatacenter.exception.CedarException;

import static com.google.common.base.Preconditions.checkNotNull;

public class ResourceTypeDetector {
  public static ResourceType detectType(String resourceType) throws CedarException {
    checkNotNull(resourceType);
    ResourceType resType = null;
    if (!Strings.isNullOrEmpty(resourceType)) {
      resType = ResourceType.forValue(resourceType);
    }
    return checkNotUnknownType(resType);
  }

  private static ResourceType checkNotUnknownType(ResourceType resourceType) throws CedarException {
    if (resourceType == null) {
      throw unknownResourceTypeException();
    }
    return resourceType;
  }

  private static CedarException unknownResourceTypeException() {
    final CedarErrorPack errorPack = new CedarErrorPack();
    errorPack.errorKey(CedarErrorKey.UNKNOWN_RESOURCE_TYPE)
        .errorType(CedarErrorType.INVALID_ARGUMENT)
        .message("Unknown requested resource type")
        .parameter("expectedTypes", ResourceType.values());
    return new CedarException(errorPack) {
    };
  }
}
