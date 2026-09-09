package org.metadatacenter.util.http;

import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ArtifactCountsTest {
  @Test void preservesZeroAndLargeTotals() throws Exception {
    var counts = ArtifactCounts.read(response(200,
        "{\"field\":0,\"element\":2,\"template\":2147483648,\"instance\":9223372036854775807}"));
    assertEquals(0, counts.field);
    assertEquals(2, counts.element);
    assertEquals(2147483648L, counts.template);
    assertEquals(Long.MAX_VALUE, counts.instance);
  }

  @Test void refusesPartialMalformedAndFailedResponses() {
    for (String body : List.of("", "null", "[]", "{}", "not json",
        "{\"field\":-1,\"element\":0,\"template\":0,\"instance\":0}",
        "{\"field\":1.5,\"element\":0,\"template\":0,\"instance\":0}",
        "{\"field\":\"1\",\"element\":0,\"template\":0,\"instance\":0}",
        "{\"field\":9223372036854775808,\"element\":0,\"template\":0,\"instance\":0}")) {
      assertThrows(CedarDependencyUnavailableException.class, () -> ArtifactCounts.read(response(200, body)), body);
    }
    for (int status : List.of(204, 302, 401, 403, 404, 500, 503)) {
      assertThrows(CedarDependencyUnavailableException.class, () -> ArtifactCounts.read(response(status,
          "{\"field\":0,\"element\":0,\"template\":0,\"instance\":0}")));
    }
    assertThrows(CedarDependencyUnavailableException.class,
        () -> ArtifactCounts.read(new BasicClassicHttpResponse(200)));
  }

  private static BasicClassicHttpResponse response(int status, String body) {
    var response = new BasicClassicHttpResponse(status);
    response.setEntity(new StringEntity(body));
    return response;
  }
}
