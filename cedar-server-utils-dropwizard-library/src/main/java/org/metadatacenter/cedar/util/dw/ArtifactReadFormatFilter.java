package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.HttpHeaders;
import org.metadatacenter.util.http.CedarResponse;

/** Select the representation before Jersey matches Accept against the resource's produces types. */
@PreMatching
public class ArtifactReadFormatFilter implements ContainerRequestFilter {
  @Override public void filter(ContainerRequestContext request) {
    if (!request.getMethod().equals("GET") || !request.getUriInfo().getPath(false).matches(
        "(?:templates|template-elements|template-fields|template-instances)/[^/]+(?:/download)?")) return;
    var formats = request.getUriInfo().getQueryParameters().get("format");
    if (formats == null) return;
    String type = formats.size() != 1 ? null : switch (formats.get(0)) {
      case "json", "jsonld" -> "application/json";
      case "yaml" -> "application/yaml";
      case "rdf-nquad" -> "application/n-quads";
      default -> null;
    };
    if (type == null) {
      request.abortWith(CedarResponse.badRequest()
          .message("format must be jsonld, json, yaml or rdf-nquad").build());
      return;
    }
    request.getHeaders().putSingle(HttpHeaders.ACCEPT, type);
  }
}
