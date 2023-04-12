package org.metadatacenter.util.http;

import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Optional;

public class PagedSortedTypedSearchQuery extends PagedSortedTypedQuery {

  private Optional<String> qInput;
  private Optional<String> isBasedOnInput;
  private Optional<String> idInput;
  private Optional<String> categoryIdInput;
  private Optional<String> modeInput;
  private String q;
  private String isBasedOn;
  private String categoryId;
  private String mode;
  private String id;

  public PagedSortedTypedSearchQuery(PaginationConfig config) {
    super(config);
  }

  public PagedSortedTypedSearchQuery isBasedOn(Optional<String> isBasedOnInput) {
    this.isBasedOnInput = isBasedOnInput;
    return this;
  }

  public PagedSortedTypedSearchQuery q(Optional<String> qInput) {
    this.qInput = qInput;
    return this;
  }

  public PagedSortedTypedSearchQuery id(Optional<String> idInput) {
    this.idInput = idInput;
    return this;
  }

  public PagedSortedTypedSearchQuery categoryId(Optional<String> categoryIdInput) {
    this.categoryIdInput = categoryIdInput;
    return this;
  }

  public PagedSortedTypedSearchQuery mode(Optional<String> modeInput) {
    this.modeInput = modeInput;
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery resourceTypes(Optional<String> resourceTypesInput) {
    super.resourceTypes(resourceTypesInput);
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery version(Optional<String> v) {
    super.version(v);
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery publicationStatus(Optional<String> v) {
    super.publicationStatus(v);
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery sort(Optional<String> sortInput) {
    super.sort(sortInput);
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery limit(Optional<Integer> limitInput) {
    super.limit(limitInput);
    return this;
  }

  @Override
  public PagedSortedTypedSearchQuery offset(Optional<Integer> offsetInput) {
    super.offset(offsetInput);
    return this;
  }

  @Override
  public void validate() throws CedarException {
    super.validate();
    validateQ();
    validateId();
    validateCategoryId();
    validateResourceTypesWithTemplateId();
    validateMode();
  }

  public String getIsBasedOn() {
    return isBasedOn;
  }

  public String getCategoryId() {
    return categoryId;
  }

  public String getQ() {
    return q;
  }

  public String getMode() {
    return mode;
  }

  public String getId() {
    return id;
  }

  private static boolean isValidURL(String urlStr) {
    try {
      URL url = new URL(urlStr);
      return true;
    } catch (MalformedURLException e) {
      return false;
    }
  }

  private void validateTemplateId() throws CedarException {
    if (isBasedOnInput.isPresent()) {
      if (isBasedOnInput.get() != null
          && !isBasedOnInput.get().isEmpty()
          && isValidURL(isBasedOnInput.get())) {
        isBasedOn = isBasedOnInput.get();
      } else {
        throw new CedarAssertionException("You must pass in 'is_based_on' as a valid artifact identifier!")
            .badRequest()
            .parameter("template_id", isBasedOnInput.get());
      }
    }
  }

  protected void validateResourceTypesWithTemplateId() throws CedarException {
    validateTemplateId();
    if (isBasedOn != null) {
      if (resourceTypesInput.isPresent()) {
        throw new CedarAssertionException(
            "You must not specify 'resource_types' if the 'is_based_on' is specified!")
            .badRequest()
            .parameter("is_based_on", isBasedOn)
            .parameter("resource_types", resourceTypesInput.get())
            .badRequest();
      } else {
        resourceTypeList = new ArrayList<>();
        resourceTypeList.add(CedarResourceType.INSTANCE);
      }
    } else {
      validateResourceTypes();
    }
  }

  public void validateQ() throws CedarException {
    if (qInput.isPresent()) {
      if (qInput.get() == null || qInput.get().trim().isEmpty()) {
        throw new CedarAssertionException("You must pass in a valid search query as 'q'!")
            .badRequest()
            .parameter("q", qInput.get());
      } else {
        q = qInput.get();
      }
    } else {
      q = null;
    }
  }

  public void validateId() throws CedarException {
    if (idInput.isPresent()) {
      if (idInput.get() == null || idInput.get().trim().isEmpty()) {
        throw new CedarAssertionException("You must pass in a valid 'id'!")
            .badRequest()
            .parameter("id", idInput.get());
      } else {
        id = idInput.get();
      }
    } else {
      id = null;
    }
  }

  public void validateCategoryId() throws CedarException {
    if (categoryIdInput.isPresent()) {
      if (categoryIdInput.get() == null || categoryIdInput.get().trim().isEmpty()) {
        throw new CedarAssertionException("You must pass in a valid 'categoryId'!")
            .badRequest()
            .parameter("categoryId", categoryIdInput.get());
      } else {
        categoryId = categoryIdInput.get();
      }
    } else {
      categoryId = null;
    }
  }

  public void validateMode() throws CedarException {
    if (modeInput.isPresent()) {
      if (modeInput.get() == null || modeInput.get().trim().isEmpty()) {
        throw new CedarAssertionException("You must pass in a valid 'modeInput'!")
            .badRequest()
            .parameter("modeInput", modeInput.get());
      } else {
        mode = modeInput.get();
      }
    } else {
      mode = null;
    }
  }

}
