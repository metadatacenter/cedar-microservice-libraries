package org.metadatacenter.util.http;

import org.metadatacenter.config.PaginationConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

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
  private Optional<String> sharingInput;
  private String q;
  private String isBasedOn;
  private String categoryId;
  private String mode;
  private String id;

  public PagedSortedTypedSearchQuery(PaginationConfig config) {
    super(config);
    this.qInput = Optional.empty();
    this.isBasedOnInput = Optional.empty();
    this.idInput = Optional.empty();
    this.categoryIdInput = Optional.empty();
    this.modeInput = Optional.empty();
    this.sharingInput = Optional.empty();
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

  /** The sharing view. The caller parses its value; it is held here to be refused where it cannot apply. */
  public PagedSortedTypedSearchQuery sharing(Optional<String> sharingInput) {
    this.sharingInput = sharingInput;
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
    validateMode();
    validateResourceTypesWithTemplateId();
  }

  /**
   * The shallow search asks OpenSearch for the whole window in one request, and OpenSearch refuses a
   * window wider than index.max_result_window. Without this the refusal arrives as a 500 for what is a
   * client mistake, so it is caught here and the caller is pointed at the endpoint that can serve it.
   * The window belongs to the index rather than to the paging policy, so it is passed in.
   */
  public void validateShallowWindow(int maxResultWindow) throws CedarException {
    if ((long) getOffset() + getLimit() > maxResultWindow) {
      throw new CedarAssertionException("You should request no more than " + maxResultWindow
          + " rows in total, counting the offset. Use /search-deep to page beyond that!")
          .parameter("offset", getOffset())
          .parameter("limit", getLimit())
          .badRequest();
    }
  }

  /**
   * The deep search walks the offset a page at a time, so a large one is a long walk rather than a
   * refusal. It is bounded because the walk is linear: the deepest offset worth serving is the one
   * that still answers well inside the gateway timeout.
   */
  public void validateDeepOffset() throws CedarException {
    int maxOffset = getPaginationConfig().getMaxOffset();
    if (getOffset() > maxOffset) {
      throw new CedarAssertionException("You should specify an offset smaller than " + maxOffset + "!")
          .parameter("offset", getOffset())
          .badRequest();
    }
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
      if (q != null) {
        throw new CedarAssertionException("Combine 'is_based_on' with filters, not a text query; 'q' and 'is_based_on' cannot be used together.")
            .badRequest().parameter("q", q).parameter("is_based_on", isBasedOn);
      }
      // The search by template lists the template's instances from the graph, ordered and paged, within any
      // last-modified window. It applies no other selector or filter, so a request naming one is refused rather
      // than answered as though it had not. A filter set to 'all' filters nothing and is honoured.
      refuseWithIsBasedOn("id", id);
      refuseWithIsBasedOn("sharing", sharingInput.filter(sharing -> !sharing.isEmpty()).orElse(null));
      refuseWithIsBasedOn("mode", mode);
      refuseWithIsBasedOn("category_id", categoryId);
      refuseWithIsBasedOn("version", version == ResourceVersionFilter.ALL ? null : getVersionAsString());
      refuseWithIsBasedOn("publication_status",
          publicationStatus == ResourcePublicationStatusFilter.ALL ? null : getPublicationStatusAsString());
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

  private void refuseWithIsBasedOn(String parameter, String value) throws CedarException {
    if (value != null) {
      throw new CedarAssertionException("A search by 'is_based_on' returns the template's instances and applies no "
          + "other selector or filter, so it cannot honour '" + parameter + "=" + value + "'. It can be combined with "
          + "'limit', 'offset', 'sort', 'modified_after' and 'modified_before'.")
          .badRequest()
          .parameter(parameter, value)
          .parameter("is_based_on", isBasedOn);
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
