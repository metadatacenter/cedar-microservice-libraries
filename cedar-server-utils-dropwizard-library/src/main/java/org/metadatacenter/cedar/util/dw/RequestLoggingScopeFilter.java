package org.metadatacenter.cedar.util.dw;

import jakarta.servlet.*;
import org.metadatacenter.server.logging.filter.ThreadLocalRequestIdHolder;
import java.io.IOException;

/** Backstop for failures and short-circuits that never reach a Jersey response filter. */
public final class RequestLoggingScopeFilter implements Filter {
  @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    ThreadLocalRequestIdHolder.clear();
    try {
      chain.doFilter(request, response);
    } finally {
      ThreadLocalRequestIdHolder.clear();
    }
  }
}
