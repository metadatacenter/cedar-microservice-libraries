package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.util.Map;

import static org.metadatacenter.util.test.PermissionMatrix.Actor.ANONYMOUS;
import static org.metadatacenter.util.test.PermissionMatrix.Actor.OWNER;

/**
 * What a diverging matrix reports about itself. The number in the failure has to be the number of
 * cells that diverged. It was once the length of the accumulated message, which reads as a
 * plausible cell count and sends a reader looking for hundreds of failures that do not exist.
 */
public class PermissionMatrixTest {

  @Test
  public void aDivergingMatrixReportsHowManyCellsDiverged() throws Exception {
    // Nothing is listening, so every cell diverges and the count is known in advance.
    PermissionMatrix matrix = new PermissionMatrix(unreachableBaseUrl(), Map.of(OWNER, "Bearer irrelevant"));
    matrix.when("GET", "/artifacts/1")
        .expect(ANONYMOUS, 401)
        .expect(OWNER, 200);

    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, matrix::verify);

    Assertions.assertEquals(2, failure.getActual().getValue(),
        "the two unreachable cells should be counted, and nothing else: " + failure.getMessage());
    Assertions.assertEquals(0, failure.getExpected().getValue());
  }

  @Test
  public void aDivergingMatrixNamesEveryCellThatDiverged() throws Exception {
    PermissionMatrix matrix = new PermissionMatrix(unreachableBaseUrl(), Map.of(OWNER, "Bearer irrelevant"));
    matrix.when("GET", "/artifacts/1")
        .expect(ANONYMOUS, 401)
        .expect(OWNER, 200);

    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, matrix::verify);

    String message = failure.getMessage();
    Assertions.assertTrue(message.contains("GET /artifacts/1 as ANONYMOUS"), message);
    Assertions.assertTrue(message.contains("GET /artifacts/1 as OWNER"), message);
  }

  @Test
  public void anEmptyMatrixFailsRatherThanPassingVacuously() {
    PermissionMatrix matrix = new PermissionMatrix("http://localhost:1", Map.of(OWNER, "Bearer irrelevant"));

    Assertions.assertThrows(AssertionFailedError.class, matrix::verify);
  }

  /** Port 1 is the backend-test convention for a deliberately unavailable loopback dependency. */
  private static String unreachableBaseUrl() {
    return "http://127.0.0.1:1";
  }

}
