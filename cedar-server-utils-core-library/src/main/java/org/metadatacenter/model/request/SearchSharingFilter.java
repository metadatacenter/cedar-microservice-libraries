package org.metadatacenter.model.request;

/** The sharing view constrains an indexed query independently of its text and resource type. */
public enum SearchSharingFilter {
  ALL(""), SHARED_WITH_ME("shared-with-me"), SHARED_WITH_EVERYBODY("shared-with-everybody");

  private final String value;
  SearchSharingFilter(String value) { this.value = value; }
  public String value() { return value; }

  public static SearchSharingFilter fromQuery(String value) {
    if (value == null || value.isEmpty()) return ALL;
    for (SearchSharingFilter filter : values()) if (filter.value.equals(value)) return filter;
    throw new IllegalArgumentException("Unknown sharing filter: " + value);
  }
}
