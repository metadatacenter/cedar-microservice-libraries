package org.metadatacenter.util.http;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.constant.HttpConstants;

import java.util.Map;

/**
 * The paging half of a listing answered in CEDAR's body envelope: the page that was asked for, how
 * many items match, where this page starts, and the links to its neighbours. A subclass adds the
 * collection under a name of its own, as the resource server's listings do.
 *
 * <p>The links repeat the request's own query string with only {@code offset} and {@code limit}
 * replaced, so a filter the caller supplied is carried to every page.
 *
 * <p>{@code countCapped} is present, and true, only when the listing stopped counting at a ceiling
 * rather than counting every match. {@code totalCount} is then a lower bound, and there is no
 * {@code last} link, because the last page is not known.
 */
public abstract class PagedListResponse {

  @Schema(description = "The page that was asked for, after defaults were applied")
  private PageRequest request;

  @Schema(description = "How many items match. A lower bound when countCapped is true")
  private long totalCount;

  @Schema(description = "The offset of the first item on this page")
  private long currentOffset;

  @JsonInclude(JsonInclude.Include.NON_DEFAULT)
  @Schema(description = "Present and true when counting stopped at a ceiling, so totalCount is a lower bound "
      + "and no last link is given")
  private boolean countCapped;

  @Schema(description = "Links to the first, previous, next and last pages, keyed by relation. A relation "
      + "with no page is absent")
  private Map<String, String> paging;

  /**
   * Fills the paging fields.
   *
   * @param requestUrl  the full URL of the request, query string included
   * @param totalCount  how many items match, or the ceiling the count stopped at
   * @param countCapped whether counting stopped at a ceiling
   */
  protected void page(String requestUrl, long totalCount, int limit, int offset, boolean countCapped) {
    this.request = new PageRequest(limit, offset);
    this.totalCount = totalCount;
    this.currentOffset = offset;
    this.countCapped = countCapped;
    Map<String, String> links = LinkHeaderUtil.getPagingLinkHeaders(requestUrl, totalCount, limit, offset);
    if (countCapped) {
      links.remove(HttpConstants.HEADER_LINK_TYPE_LAST);
    }
    this.paging = links;
  }

  public PageRequest getRequest() {
    return request;
  }

  public long getTotalCount() {
    return totalCount;
  }

  public long getCurrentOffset() {
    return currentOffset;
  }

  public boolean isCountCapped() {
    return countCapped;
  }

  public Map<String, String> getPaging() {
    return paging;
  }

  /** The limit and offset a page was served with. */
  public record PageRequest(int limit, int offset) {
  }
}
