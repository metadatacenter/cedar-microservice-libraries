package org.metadatacenter.server.search.elasticsearch.service;

import org.metadatacenter.model.response.FolderServerNodeListResponse;

/**
 * A page of a caller-driven deep search, and the position the next one resumes from.
 *
 * @param response        the page, assembled as any other listing
 * @param pointInTimeId   the snapshot the walk continues in, or null when this page was the last
 * @param nextSearchAfter the sort position the next page resumes at, or null when there is no next page
 */
public record DeepSearchPageResponse(FolderServerNodeListResponse response, String pointInTimeId,
                                     Object[] nextSearchAfter) {

  public boolean hasMore() {
    return pointInTimeId != null && nextSearchAfter != null;
  }
}
