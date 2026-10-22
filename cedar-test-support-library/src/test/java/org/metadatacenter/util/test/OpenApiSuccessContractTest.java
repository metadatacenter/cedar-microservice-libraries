package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * The rule the server suites hold their committed OpenAPI documents to on the success side: every
 * operation declares a 2xx, every payload is described, every schema reference resolves, and the
 * exemptions a suite lists are still true. Each defect the rule names is planted in a document, so a
 * check that stopped looking for one would pass that document and fail here.
 */
public class OpenApiSuccessContractTest {

  private static final String THING = "{\"$ref\":\"#/components/schemas/Thing\"}";

  @Test
  public void aDescribedDocumentPassesWithItsExemptions() throws Exception {
    OpenApiSuccessContract.assertDescribed(document("""
        "/things": {
          "post": {
            "requestBody": {"content": {"application/json": {"schema": %1$s}}},
            "responses": {"201": {"content": {"application/json": {"schema": %1$s}}}}}},
        "/things/{id}": {
          "get": {"responses": {"200": {"$ref": "#/components/responses/OneThing"}}},
          "put": {"requestBody": {"content": {"application/json": {"schema": %1$s}}},
                  "responses": {"204": {"description": "stored"}}},
          "delete": {"responses": {"202": {"description": "accepted"}}}},
        "/command/reindex": {"post": {"responses": {"200": {"content": {"application/json": {"schema": %1$s}}}}}}
        """.formatted(THING), ""), "POST /command/reindex", "DELETE /things/{id} 202");
  }

  @Test
  public void everyUndescribedPayloadIsReportedAtItsCoordinate() {
    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiSuccessContract.assertDescribed(document("""
            "/no-success": {"get": {"responses": {"404": {"description": "missing"}}}},
            "/no-payload": {"get": {"responses": {"200": {"description": "ok"}}}},
            "/no-schema": {"get": {"responses": {"200": {"content": {"application/json": {}}}}}},
            "/bare-string": {"get": {"responses": {
              "200": {"content": {"application/yaml": {"schema": {"type": "string"}}}}}}},
            "/bare-body": {"post": {
              "requestBody": {"content": {"application/json": {"schema": {"type": "string"}}}},
              "responses": {"204": {"description": "done"}}}},
            "/no-body": {"patch": {"responses": {"204": {"description": "done"}}}},
            "/dangling": {"get": {"responses": {
              "200": {"content": {"application/json": {"schema": {"$ref": "#/components/schemas/Missing"}}}}}}}
            """, ",\"Empty\": {\"type\": \"object\"}")));

    String message = failure.getMessage();
    for (String finding : List.of(
        "GET /no-success: declares no 2xx response",
        "GET /no-payload 200: describes no payload",
        "GET /no-schema 200 (application/json): declares no schema",
        "GET /bare-string 200 (application/yaml): typed as a bare string",
        "POST /bare-body request body (application/json): typed as a bare string",
        "PATCH /no-body: declares no request body and is not listed as bodiless",
        "unresolved schema reference: #/components/schemas/Missing",
        "schema Empty: defines no properties")) {
      Assertions.assertTrue(message.contains(finding), "not reported: " + finding + "\n" + message);
    }
  }

  @Test
  public void anExemptionThatIsNoLongerTrueFails() {
    String paths = """
        "/things": {"post": {
          "requestBody": {"content": {"application/json": {"schema": %1$s}}},
          "responses": {"201": {"content": {"application/json": {"schema": %1$s}}}}}}
        """.formatted(THING);

    AssertionFailedError bodiless = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiSuccessContract.assertDescribed(document(paths, ""), "POST /things"));
    Assertions.assertTrue(bodiless.getMessage().contains("POST /things"), bodiless.getMessage());

    AssertionFailedError empty = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiSuccessContract.assertDescribed(document(paths, ""), "DELETE /things 202"));
    Assertions.assertTrue(empty.getMessage().contains("DELETE /things 202"), empty.getMessage());

    AssertionFailedError described = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiSuccessContract.assertDescribed(document(paths, ""), "POST /things 201"));
    Assertions.assertTrue(described.getMessage().contains("POST /things 201"), described.getMessage());
  }

  /** An empty-response exemption covers only a response that declares no content, so it hides no finding. */
  @Test
  public void anEmptyResponseExemptionDoesNotExcuseAPayloadItDescribes() {
    AssertionFailedError failure = Assertions.assertThrows(AssertionFailedError.class, () ->
        OpenApiSuccessContract.assertDescribed(document("""
            "/things": {"get": {"responses": {
              "200": {"content": {"application/json": {"schema": {"type": "string"}}}}}}}
            """, ""), "GET /things 200"));

    Assertions.assertTrue(failure.getMessage().contains("GET /things 200 (application/json): typed as a bare string"),
        failure.getMessage());
  }

  private static InputStream document(String paths, String extraSchemas) {
    String json = """
        {"paths": {%s},
         "components": {
           "schemas": {"Thing": {"type": "object", "properties": {"name": {"type": "string"}}}%s},
           "responses": {"OneThing": {"content": {"application/json": {"schema": %s}}}}}}
        """.formatted(paths, extraSchemas, THING);
    return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
  }
}
