package org.metadatacenter.util.http;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.ws.rs.core.Response;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConstants;

import java.io.IOException;
import java.util.*;

/** Protocol and representation metadata shared by buffered application-to-application relays.
 * Cookies and transport framing never cross this boundary; HttpClient has already decoded the body.
 * Callers retain their own access and caching policy (for example OpenView's mandatory no-store).
 */
public final class ResponseRelay {
  private ResponseRelay() { }

  private static final Set<String> FORWARDED = Set.of("content-type", "etag", "vary", "last-modified",
      "cache-control", "expires", "content-language", "content-disposition", "retry-after",
      "www-authenticate", "allow", "location", "link",
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS.toLowerCase(Locale.ROOT),
      CustomHttpConstants.HEADER_CEDAR_VALIDATION_REPORT.toLowerCase(Locale.ROOT),
      HttpConstants.HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS.toLowerCase(Locale.ROOT));

  private static List<Header> headers(ClassicHttpResponse upstream) {
    Set<String> connectionHeaders = new HashSet<>();
    for (Header h : upstream.getHeaders("Connection")) {
      for (String token : h.getValue().split(",")) connectionHeaders.add(token.trim().toLowerCase(Locale.ROOT));
    }
    return Arrays.stream(upstream.getHeaders()).filter(h -> {
      String name = h.getName().toLowerCase(Locale.ROOT);
      return FORWARDED.contains(name) && !connectionHeaders.contains(name);
    }).toList();
  }

  /** Replace an existing field once, then append every remaining value, regardless of header casing. */
  public static void copyHeaders(ClassicHttpResponse upstream, HttpServletResponse downstream) {
    Set<String> seen = new HashSet<>();
    for (Header h : headers(upstream)) {
      if (seen.add(h.getName().toLowerCase(Locale.ROOT))) downstream.setHeader(h.getName(), h.getValue());
      else downstream.addHeader(h.getName(), h.getValue());
    }
  }

  /** Preserve status and buffered bytes; the enclosing resource closes the upstream response. */
  public static Response.ResponseBuilder responseBuilder(ClassicHttpResponse upstream) throws IOException {
    int status = upstream.getCode();
    Response.ResponseBuilder result = Response.status(status);
    for (Header h : headers(upstream)) result.header(h.getName(), h.getValue());
    if (status >= 200 && status != 204 && status != 205 && status != 304 && upstream.getEntity() != null) {
      result.entity(EntityUtils.toByteArray(upstream.getEntity()));
    }
    return result;
  }
}
