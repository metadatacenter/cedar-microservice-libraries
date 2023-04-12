package org.metadatacenter.util.http;

import org.metadatacenter.exception.CedarException;

import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.Optional;

public class CedarURIBuilder {

  private UriInfo uriInfo;
  private UriBuilder builder;

  private CedarURIBuilder() {
  }

  public CedarURIBuilder(UriInfo uriInfo) {
    this.uriInfo = uriInfo;
    builder = uriInfo.getAbsolutePathBuilder();
  }

  public CedarURIBuilder queryParam(String name, Optional value) {
    if (value != null && value.isPresent()) {
      builder.queryParam(name, value.get());
    }
    return this;
  }

  public URI build() {
    return builder.build();
  }

  public String getProxyUrl(String newServerBase) throws CedarException {
    String requestUri = uriInfo.getRequestUri().toString();
    String baseUriString = uriInfo.getBaseUri().toString();
    uriInfo.getRequestUri();
    return newServerBase + requestUri.substring(baseUriString.length());
  }
}
