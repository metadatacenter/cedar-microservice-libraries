package org.metadatacenter.util.http;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.metadatacenter.constant.HttpConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class LinkHeaderUtil {

  private static final Logger log = LoggerFactory.getLogger(LinkHeaderUtil.class);

  public static final String PARAM_OFFSET = "offset";
  public static final String PARAM_LIMIT = "limit";

  private LinkHeaderUtil() {
  }

  public static String getPagingLinkHeader(String baseUrl, Long total, Integer limit, Integer offset) {

    Map<String, String> pagingLinkHeaders = getPagingLinkHeaders(baseUrl, total, limit, offset);

    StringBuilder links = new StringBuilder();
    pagingLinkHeaders.forEach((linkType, uri) -> appendPagingLinkHeader(links, linkType, uri));

    return links.toString();
  }

  public static Map<String, String> getPagingLinkHeaders(URI uri, Long total, Integer limit, Integer offset) {
    return getPagingLinkHeaders(uri.toString(), total, limit, offset);
  }

  public static Map<String, String> getPagingLinkHeaders(String baseUrl, Long total, Integer limit, Integer offset) {
    if (limit == null) {
      limit = 0;
    }
    if (offset == null) {
      offset = 0;
    }

    Map<String, String> ret = new HashMap<>();
    if (offset + limit < total) {
      URI next = createOnePagingLink(baseUrl, offset + limit, limit);
      ret.put(HttpConstants.HEADER_LINK_TYPE_NEXT, next.toString());
    }

    URI last = createOnePagingLink(baseUrl, ((total - 1) / limit) * limit, limit);
    ret.put(HttpConstants.HEADER_LINK_TYPE_LAST, last.toString());

    URI first = createOnePagingLink(baseUrl, 0, limit);
    ret.put(HttpConstants.HEADER_LINK_TYPE_FIRST, first.toString());

    if (offset - limit >= 0) {
      URI prev = createOnePagingLink(baseUrl, offset - limit, limit);
      ret.put(HttpConstants.HEADER_LINK_TYPE_PREV, prev.toString());
    }

    return ret;
  }

  private static void appendPagingLinkHeader(StringBuilder sb, String linkType, String uri) {
    if (uri != null) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append("<");
      sb.append(uri);
      sb.append(">");
      sb.append("; rel=\"");
      sb.append(linkType);
      sb.append("\"");
    }
  }

  private static URI createOnePagingLink(String baseUrl, long offset, long limit) {
    URI uri = null;
    try {
      URIBuilder ub = new URIBuilder(baseUrl);
      List<NameValuePair> params = new ArrayList<>();
      params.add(new BasicNameValuePair(PARAM_OFFSET, String.valueOf(offset)));
      params.add(new BasicNameValuePair(PARAM_LIMIT, String.valueOf(limit)));
      ub.addParameters(params);
      ub.setCharset(StandardCharsets.UTF_8);
      uri = ub.build();
    } catch (URISyntaxException e) {
      log.error("There was an error while creating page link", e);
    }
    return uri;
  }

}
