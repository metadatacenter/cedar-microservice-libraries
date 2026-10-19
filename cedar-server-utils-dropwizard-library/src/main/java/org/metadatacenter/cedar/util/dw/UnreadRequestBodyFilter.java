package org.metadatacenter.cedar.util.dw;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.eclipse.jetty.ee10.servlet.ServletContextRequest;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpHeaderValue;
import org.eclipse.jetty.server.HttpStream;

import java.io.IOException;

/** Announces HTTP/1 connection closure before an early response commits its headers. */
public final class UnreadRequestBodyFilter implements Filter {
  private static final String INSTALLED = UnreadRequestBodyFilter.class.getName();

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    if (request instanceof HttpServletRequest http &&
        (http.getProtocol().equals("HTTP/1.1") || http.getProtocol().equals("HTTP/1.0")) &&
        request.getAttribute(INSTALLED) == null) {
      var jettyRequest = ServletContextRequest.getServletContextRequest(request);
      request.setAttribute(INSTALLED, Boolean.TRUE);
      long length = http.getContentLengthLong();
      boolean chunked = http.getHeader("Transfer-Encoding") != null;
      jettyRequest.addHttpStreamWrapper(stream -> new HttpStream.Wrapper(stream) {
        @Override
        public void prepareResponse(HttpFields.Mutable headers) {
          super.prepareResponse(headers);
          var input = jettyRequest.getServletChannel().getHttpInput();
          boolean unread = length > 0 ? input.getContentConsumed() < length : chunked && !input.isFinished();
          if (jettyRequest.getServletContextResponse().getStatus() >= 200 && unread) {
            // Jetty may discover unread content only after the response is committed, then
            // close without warning. A pooled client can reuse that connection before seeing
            // its EOF. Do not drain here: a delayed body must not hold a refusal open, and a
            // streaming application may still consume input after starting its response.
            headers.put(HttpHeader.CONNECTION, HttpHeaderValue.CLOSE);
          }
        }
      });
    }
    chain.doFilter(request, response);
  }
}
