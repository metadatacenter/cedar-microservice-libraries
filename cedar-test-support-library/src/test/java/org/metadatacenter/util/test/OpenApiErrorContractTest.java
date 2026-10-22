package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * The rule the server suites hold their committed OpenAPI documents to: every 4xx and 5xx answers
 * with the CEDAR error envelope, unless its exact coordinate is listed as an exception that is still
 * true. A document with one defect is checked against it, so a check that stopped looking would pass
 * that document and fail here.
 */
public class OpenApiErrorContractTest {

  private static final String CEDAR_ERROR = "{\"$ref\":\"#/components/schemas/CedarError\"}";

  @Test
  public void everyErrorUsingTheEnvelopeDirectlyOrThroughAComponentPasses() throws Exception {
    OpenApiErrorContract.assertDocumented(document("""
        "/things/{id}": {
          "get": {"responses": {
            "200": {"description": "ok"},
            "404": {"content": {"application/json": {"schema": %s}}},
            "500": {"$ref": "#/components/responses/ServerError"}}}},
        "/healthcheck": {"get": {"responses": {"500": {"description": "unhealthy"}}}}
        """.formatted(CEDAR_ERROR)));
  }

  @Test
  public void anErrorWithAnotherShapeFailsAtItsCoordinate() {
    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiErrorContract.assertDocumented(document("""
            "/things/{id}": {"delete": {"responses": {
              "403": {"content": {"application/json": {"schema": {"type": "string"}}}}}}}
            """)));

    Assertions.assertTrue(failure.getMessage().startsWith("DELETE /things/{id} 403"), failure.getMessage());
  }

  @Test
  public void aListedExceptionExcusesOnlyItsCoordinateAndAStaleOneFails() throws Exception {
    String document = """
        "/things/{id}": {"put": {"responses": {
          "409": {"description": "plain text"},
          "422": {"content": {"application/json": {"schema": %s}}}}}}
        """.formatted(CEDAR_ERROR);

    OpenApiErrorContract.assertDocumented(document(document), "PUT /things/{id} 409");

    AssertionFailedError stale = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiErrorContract.assertDocumented(document(document), "PUT /things/{id} 409", "GET /gone 404"));
    Assertions.assertTrue(stale.getMessage().contains("GET /gone 404"), stale.getMessage());
  }

  @Test
  public void aDocumentWithoutTheEnvelopeSchemaFails() {
    InputStream input = new ByteArrayInputStream("{\"paths\": {}, \"components\": {\"schemas\": {}}}"
        .getBytes(StandardCharsets.UTF_8));

    Assertions.assertThrows(AssertionFailedError.class, () -> OpenApiErrorContract.assertDocumented(input));
  }

  private static InputStream document(String paths) {
    String json = """
        {"paths": {%s},
         "components": {
           "schemas": {"CedarError": {"type": "object", "properties": {"errorKey": {"type": "string"}}}},
           "responses": {"ServerError": {"content": {"application/json": {"schema": %s}}}}}}
        """.formatted(paths, CEDAR_ERROR);
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }
}
