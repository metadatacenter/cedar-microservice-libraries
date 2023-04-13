package org.metadatacenter.model.request;

import java.util.Optional;

public class NodeListQueryTypeDetector {
  public static NodeListQueryType detect(Optional<String> q, Optional<String> id, Optional<String> isBasedOn, Optional<String> sharing,
                                         Optional<String> mode, Optional<String> categoryId) {

    if (id != null && id.isPresent() && !id.get().isEmpty()) {
      return NodeListQueryType.SEARCH_ID;
    }

    if ((q == null || !q.isPresent() || q.get().isEmpty() || "*".equals(q.get())) &&
        (isBasedOn == null || !isBasedOn.isPresent() || isBasedOn.get().isEmpty()) &&
        (sharing == null || !sharing.isPresent() || sharing.get().isEmpty()) &&
        (mode == null || !mode.isPresent() || mode.get().isEmpty()) &&
        (categoryId == null || !categoryId.isPresent() || categoryId.get().isEmpty())) {
      return NodeListQueryType.VIEW_ALL;
    }

    if (q != null && q.isPresent() && !q.get().isEmpty()) {
      return NodeListQueryType.SEARCH_TERM;
    }

    if (isBasedOn != null && isBasedOn.isPresent() && !isBasedOn.get().isEmpty()) {
      return NodeListQueryType.SEARCH_IS_BASED_ON;
    }

    if (sharing != null && sharing.isPresent() && !sharing.get().isEmpty()) {
      if ("shared-with-me".equals(sharing.get())) {
        return NodeListQueryType.VIEW_SHARED_WITH_ME;
      } else if ("shared-with-everybody".equals(sharing.get())) {
        return NodeListQueryType.VIEW_SHARED_WITH_EVERYBODY;
      }
    }

    if (mode != null && mode.isPresent() && !mode.get().isEmpty()) {
      if ("special-folders".equals(mode.get())) {
        return NodeListQueryType.VIEW_SPECIAL_FOLDERS;
      }
    }

    if (categoryId != null && categoryId.isPresent() && !categoryId.get().isEmpty()) {
      return NodeListQueryType.SEARCH_CATEGORY_ID;
    }

    return NodeListQueryType.UNKNOWN;
  }
}
