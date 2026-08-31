package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.constant.CustomHttpConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.util.http.CedarResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The headers a browser may read across origins, asserted in one place.
 *
 * <p>The list was maintained twice, in the response builder and in the shared bootstrap. Both now read
 * {@link CustomHttpConstants#EXPOSED_HEADERS}, and this holds them to it.
 */
class ExposedHeadersTest {

  @Test
  @DisplayName("The paging headers the artifact server sends are readable cross-origin")
  void pagingHeadersAreExposed() {
    assertTrue(CustomHttpConstants.EXPOSED_HEADERS.contains("Link"),
        "AbstractArtifactCrudResource sends Link, which is unreadable cross-origin unless exposed");
    assertTrue(CustomHttpConstants.EXPOSED_HEADERS.contains(CustomHttpConstants.HEADER_TOTAL_COUNT),
        "it sends Total-Count for the same reason");
  }

  @Test
  @DisplayName("The headers already relied on are still exposed")
  void previouslyExposedHeadersRemain() {
    assertTrue(CustomHttpConstants.EXPOSED_HEADERS.contains("ETag"),
        "conditional requests need the client to read the ETag it must send back in If-Match");
    assertTrue(CustomHttpConstants.EXPOSED_HEADERS.contains(CustomHttpConstants.HEADER_CEDAR_VALIDATION_STATUS));
    assertTrue(CustomHttpConstants.EXPOSED_HEADERS.contains("Content-Disposition"));
  }

  @Test
  @DisplayName("The bootstrap and the response builder expose the same set")
  void bothSourcesAgree() {
    assertEquals(CustomHttpConstants.EXPOSED_HEADERS, CedarMicroserviceApplication.HTTP_EXPOSED_HEADERS,
        "the bootstrap's CORS filter and the response builder must name the same headers");

    Response response = CedarResponse.badRequest().build();
    String sent = response.getHeaderString(HttpConstants.HTTP_HEADER_ACCESS_CONTROL_EXPOSE_HEADERS);
    for (String header : CustomHttpConstants.EXPOSED_HEADERS) {
      assertTrue(sent != null && sent.contains(header),
          "the builder should expose " + header + ", sent: " + sent);
    }
  }
}
