package org.metadatacenter.util.http;

import org.metadatacenter.exception.CedarException;

import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class CedarURIBuilder {

  private UriInfo uriInfo;
  private UriBuilder builder;
  private final List<Object> queryValues = new ArrayList<>();

  private CedarURIBuilder() {
  }

  public CedarURIBuilder(UriInfo uriInfo) {
    this.uriInfo = uriInfo;
    builder = uriInfo.getAbsolutePathBuilder();
  }

  public CedarURIBuilder queryParam(String name, Optional<?> value) {
    if (value != null && value.isPresent()) {
      // queryParam treats %xx as already encoded and braces as URI templates. Request
      // parameters are decoded data: substitute them as values so both survive literally.
      builder.queryParam(name, "{cedarQuery" + queryValues.size() + "}");
      queryValues.add(value.get());
    }
    return this;
  }

  public URI build() {
    return builder.build(queryValues.toArray());
  }

  public String getProxyUrl(String newServerBase) throws CedarException {
    String requestUri = uriInfo.getRequestUri().toString();
    String baseUriString = uriInfo.getBaseUri().toString();
    uriInfo.getRequestUri();
    return newServerBase + requestUri.substring(baseUriString.length());
  }
}
