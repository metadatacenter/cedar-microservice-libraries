package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;

/** Keep revision validators usable for If-Match across transforming reverse proxies. */
public final class StrongEtagResponseFilter implements ContainerResponseFilter {
  @Override
  public void filter(ContainerRequestContext request, ContainerResponseContext response) {
    EntityTag tag = response.getEntityTag();
    if (tag == null || tag.isWeak()) {
      return;
    }
    // Cloudflare's gzip/Brotli conversion weakens strong ETags. Such a tag can never satisfy
    // If-Match, even on the first edit. Preserve the origin representation, not just its revision.
    String cacheControl = response.getHeaderString(HttpHeaders.CACHE_CONTROL);
    if (cacheControl == null || !CacheControl.valueOf(cacheControl).isNoTransform()) {
      response.getHeaders().add(HttpHeaders.CACHE_CONTROL, "no-transform");
    }
  }
}
