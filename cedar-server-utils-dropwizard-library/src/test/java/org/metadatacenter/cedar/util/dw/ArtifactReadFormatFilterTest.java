package org.metadatacenter.cedar.util.dw;

import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactReadFormatFilterTest {
  private ContainerRequest request(String method, String path) {
    var request = new ContainerRequest(URI.create("http://localhost/"), URI.create("http://localhost/" + path),
        method, null, new MapPropertiesDelegate());
    request.getHeaders().putSingle("Accept", "text/html");
    new ArtifactReadFormatFilter().filter(request);
    return request;
  }

  @Test void explicitFormatsOverrideEvenAnIncompatibleAcceptOnEveryReadAndDownload() {
    for (String kind : new String[]{"templates", "template-elements", "template-fields", "template-instances"}) {
      for (String suffix : new String[]{"", "/download"}) {
        for (var format : Map.of("jsonld", "application/json", "json", "application/json",
            "yaml", "application/yaml", "rdf-nquad", "application/n-quads").entrySet()) {
          var result = request("GET", kind + "/123" + suffix + "?format=" + format.getKey());
          assertNull(result.getAbortResponse());
          assertEquals(format.getValue(), result.getHeaderString("Accept"));
        }
      }
    }
  }

  @Test void anEncodedFullIdentifierIsStillOnePathSegment() {
    assertEquals("application/yaml", request("GET", "templates/https%3A%2F%2Frepo.example%2Ftemplates%2F123?format=yaml").getHeaderString("Accept"));
  }

  @Test void invalidOrAmbiguousFormatsAreBadRequests() {
    for (String format : new String[]{"xml", "", "yaml&format=json"}) {
      assertEquals(400, request("GET", "templates/123?format=" + format).getAbortResponse().getStatus());
    }
  }

  @Test void writesListingsAndUnrelatedRoutesKeepTheirAcceptHeader() {
    for (String path : new String[]{"templates", "templates/123/details", "command/convert", "templates/123"}) {
      assertEquals("text/html", request("POST", path + "?format=yaml").getHeaderString("Accept"));
    }
    assertEquals("text/html", request("GET", "templates/123").getHeaderString("Accept"));
    assertEquals("text/html", request("GET", "templates?format=yaml").getHeaderString("Accept"));
  }
}
