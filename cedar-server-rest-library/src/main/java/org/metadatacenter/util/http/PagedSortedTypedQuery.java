package org.metadatacenter.util.http;

import org.apache.commons.lang.StringUtils;
import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.util.CedarResourceTypeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class PagedSortedTypedQuery extends PagedSortedQuery {

  protected Optional<String> resourceTypesInput;
  protected List<CedarResourceType> resourceTypeList;
  protected Optional<String> versionInput;
  protected ResourceVersionFilter version;
  protected Optional<String> publicationStatusInput;
  protected ResourcePublicationStatusFilter publicationStatus;

  public PagedSortedTypedQuery(PaginationConfig config) {
    super(config);

    this.resourceTypeList = new ArrayList<>();
  }

  public PagedSortedTypedQuery resourceTypes(Optional<String> resourceTypesInput) {
    this.resourceTypesInput = resourceTypesInput;
    return this;
  }

  public PagedSortedTypedQuery version(Optional<String> versionInput) {
    this.versionInput = versionInput;
    return this;
  }

  public PagedSortedTypedQuery publicationStatus(Optional<String> publicationStatusInput) {
    this.publicationStatusInput = publicationStatusInput;
    return this;
  }

  @Override
  public PagedSortedTypedQuery sort(Optional<String> sortInput) {
    super.sort(sortInput);
    return this;
  }

  @Override
  public PagedSortedTypedQuery limit(Optional<Integer> limitInput) {
    super.limit(limitInput);
    return this;
  }

  @Override
  public PagedSortedTypedQuery offset(Optional<Integer> offsetInput) {
    super.offset(offsetInput);
    return this;
  }

  @Override
  public void validate() throws CedarException {
    validateLimit();
    validateOffset();
    validateSorting();
    validateResourceTypes();
    validateVersion();
    validatePublicationStatus();
  }

  public List<CedarResourceType> getResourceTypeList() {
    return resourceTypeList;
  }

  public List<String> getResourceTypeAsStringList() {
    List<String> r = new ArrayList<>();
    for (CedarResourceType nt : resourceTypeList) {
      r.add(nt.getValue());
    }
    return r;
  }

  public String getResourceTypesAsString() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < resourceTypeList.size(); i++) {
      sb.append(resourceTypeList.get(i).getValue());
      if (i != resourceTypeList.size() - 1) {
        sb.append(",");
      }
    }
    return sb.toString();
  }

  protected void validateResourceTypes() throws CedarException {
    String resourceTypesString = null;
    List<String> resourceTypeStringList;
    if (resourceTypesInput.isEmpty()) {
      resourceTypeStringList = CedarResourceTypeUtil.getValidResourceTypeValuesForRestCalls();
    } else {
      resourceTypesString = resourceTypesInput.get();
      resourceTypesString = resourceTypesString.trim();
      if (resourceTypesString.isEmpty()) {
        throw new CedarAssertionException("If present, 'resource_types' must be a comma separated list!")
            .badRequest()
            .parameter("resource_types", resourceTypesString);
      }
      resourceTypeStringList = Arrays.asList(StringUtils.split(resourceTypesString, ","));
    }
    resourceTypeList = new ArrayList<>();
    for (String rt : resourceTypeStringList) {
      CedarResourceType crt = CedarResourceType.forValue(rt);
      if (CedarResourceTypeUtil.isNotValidForRestCall(crt)) {
        throw new CedarAssertionException("You passed an illegal 'resource_types':'" + rt + "'. The allowed values " +
            "are:" +
            CedarResourceTypeUtil.getValidResourceTypeValuesForRestCalls())
            .errorKey(CedarErrorKey.INVALID_RESOURCE_TYPE)
            .badRequest()
            .parameter("resource_types", resourceTypesString)
            .parameter("invalidResourceTypes", rt)
            .parameter("allowedResourceTypes", CedarResourceTypeUtil.getValidResourceTypeValuesForRestCalls());
      } else {
        resourceTypeList.add(crt);
      }
    }
  }

  private void validateVersion() throws CedarException {
    if (versionInput.isEmpty()) {
      version = ResourceVersionFilter.ALL;
    } else {
      String versionString = versionInput.get();
      if (versionString.isEmpty()) {
        throw new CedarAssertionException("If present, 'version' must be one of the following:" +
            ResourceVersionFilter.getValidValues())
            .badRequest()
            .parameter("version", versionString);
      }
      version = ResourceVersionFilter.forValue(versionString);
      if (version == null) {
        throw new CedarAssertionException("Invalid value for 'version'! Must be one of the following:" +
            ResourceVersionFilter.getValidValues())
            .badRequest()
            .parameter("version", versionString);
      }
    }
  }

  private void validatePublicationStatus() throws CedarException {
    if (publicationStatusInput.isEmpty()) {
      publicationStatus = ResourcePublicationStatusFilter.ALL;
    } else {
      String publicationStatusString = publicationStatusInput.get();
      if (publicationStatusString.isEmpty()) {
        throw new CedarAssertionException("If present, 'publicationStatus' must be one of the following:" +
            ResourcePublicationStatusFilter.getValidValues())
            .badRequest()
            .parameter("publicationStatus", publicationStatusString);
      }
      publicationStatus = ResourcePublicationStatusFilter.forValue(publicationStatusString);
      if (publicationStatus == null) {
        throw new CedarAssertionException("Invalid value for 'publicationStatus'! Must be one of the following:" +
            ResourcePublicationStatusFilter.getValidValues())
            .badRequest()
            .parameter("publicationStatus", publicationStatusString);
      }
    }
  }

  public ResourceVersionFilter getVersion() {
    return version;
  }

  public ResourcePublicationStatusFilter getPublicationStatus() {
    return publicationStatus;
  }

  public String getVersionAsString() {
    return version != null ? version.getValue() : "";
  }

  public String getPublicationStatusAsString() {
    return publicationStatus != null ? publicationStatus.getValue() : "";
  }
}
