package org.metadatacenter.server.logging.query;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.logging.query.LogBoards.Board;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

/**
 * The boards page does not build a spec — it takes the spec the catalog served, overrides the range
 * and source, and POSTs it back. So whatever the server serializes has to be something the server
 * will accept, and CEDAR microservices deliberately reject unknown properties
 * ({@code CedarMicroserviceApplication} enables FAIL_ON_UNKNOWN_PROPERTIES).
 * <p>
 * This is the test that was missing: a derived accessor leaked onto the wire as {@code "grouped"},
 * and every board answered 400 because the catalog's own output was not valid input.
 */
class LogBoardsRoundTripTest {

  /** Configured the way the microservices configure theirs — strict, like the real endpoint. */
  private static ObjectMapper strictMapper() {
    return new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Test
  void everyBoardSpecSurvivesTheRoundTripThePagePerforms() {
    ObjectMapper m = strictMapper();
    List<String> failures = new ArrayList<>();
    for (Board b : LogBoards.all()) {
      if (b.spec() == null) {
        continue;
      }
      try {
        m.readValue(m.writeValueAsString(b.spec()), LogQuerySpec.class);
      } catch (Exception e) {
        failures.add(b.id() + "  ->  " + e.getMessage().split("\n")[0]);
      }
    }
    if (!failures.isEmpty()) {
      fail("Board specs the server cannot read back (" + failures.size() + "):\n  "
          + String.join("\n  ", failures));
    }
  }

  @Test
  void serializedSpecCarriesNoDerivedFields() throws Exception {
    String json = strictMapper().writeValueAsString(LogBoards.all().get(0).spec());
    if (json.contains("\"grouped\"")) {
      fail("isGrouped() leaked onto the wire as \"grouped\"; it is derived from groupBy and the "
          + "endpoint rejects it on the way back in. Keep it @JsonIgnore. Got: " + json);
    }
  }
}
