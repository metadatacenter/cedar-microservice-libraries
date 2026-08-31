package org.metadatacenter.server.search.elasticsearch.worker;

/**
 * One page of a deep search, with what the next page needs to resume.
 *
 * @param result        the rows of this page, and the total when the walk has just started
 * @param pointInTimeId the snapshot the rest of the walk continues in, or null once this page was the
 *                      last one and the snapshot has been released
 * @param nextSearchAfter the sort position the next page resumes at, or null when there is no next page
 */
public record DeepSearchPage(SearchResponseResult result, String pointInTimeId, Object[] nextSearchAfter) {
}
