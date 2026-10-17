package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.exception.security.AuthorizationNotFoundException;
import org.metadatacenter.util.http.CedarResponse;

import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises production mapper selection and the bytes Jetty sends, without external backends. */
class ResponseContractMatrixTest {
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Set<String> ERROR_FIELDS = Set.of("status", "statusCode", "errorKey", "errorReasonKey",
      "errorType", "message", "parameters", "objects", "entities", "suggestedAction", "operation", "errorId");
  private static DropwizardTestSupport<Configuration> server;
  private static java.nio.file.Path config;
  private static final HttpClient CLIENT = HttpClient.newHttpClient();

  public static class TestApplication extends Application<Configuration> {
    @Override public void run(Configuration configuration, Environment environment) {
      CedarMicroserviceApplication.registerExceptionMappers(environment);
      environment.jersey().register(new Fixture());
    }
  }

  public record Input(@NotBlank String name) {}

  @Path("/responses") @Produces("application/json")
  public static class Fixture {
    @GET @Path("direct/{status}") public Response direct(@PathParam("status") int status) {
      return CedarResponse.status(status).build();
    }
    @GET @Path("framework/{status}") public Response framework(@PathParam("status") int status) {
      throw new WebApplicationException(Response.status(status).header("Retry-After", "30")
          .header("Cache-Control", "no-store").build());
    }
    @GET @Path("authentication") public Response authentication() throws AuthorizationNotFoundException {
      throw new AuthorizationNotFoundException();
    }
    @GET @Path("challenge") public Response challenge() {
      throw new NotAuthorizedException("Basic realm=\"upstream\"", new Object[]{"Bearer realm=\"cedar\""});
    }
    @GET @Path("quota/{status}") public Response quota(@PathParam("status") int status) {
      throw new org.metadatacenter.cedar.util.dw.ratelimit.UserRateLimitException(status,"writes",7);
    }
    @GET @Path("crash") public Response crash() { throw new IllegalArgumentException("private backend detail"); }
    @GET @Path("domain") public Response domain() throws CedarProcessingException {
      throw new CedarProcessingException(new IllegalStateException("private backend detail"));
    }
    @GET @Path("explicit") public Response explicit() {
      throw new WebApplicationException(Response.status(409).header("Cache-Control", "no-store")
          .entity(Map.of("outcome", "blocked")).build());
    }
    @GET @Path("empty") public Response empty() { return CedarResponse.ok().build(); }
    @GET @Path("no-content") public Response noContent() { return CedarResponse.noContent().entity("discard me").build(); }
    @GET @Path("created") public Response created() {
      return CedarResponse.created(URI.create("/responses/item")).header("ETag", "\"7\"")
          .entity(Map.of("name", "created")).build();
    }
    @POST @Path("input") @Consumes("application/json")
    public Input input(@Valid Input input) { return input; }
    @GET @Path("query") public Map<String, Integer> query(@QueryParam("limit") int limit) { return Map.of("limit", limit); }
    @GET @Path("optional") public Map<String, Integer> optional(@QueryParam("offset") Optional<Integer> offset) {
      return Map.of("offset", offset.orElse(0));
    }
    @GET @Path("each") public Map<String, Integer> each(@QueryParam("n") List<Long> n) { return Map.of("n", n.size()); }
    @GET @Path("count/{n}") public Map<String, Integer> count(@PathParam("n") int n) { return Map.of("n", n); }
    @GET @Path("header") public Map<String, Integer> header(@HeaderParam("X-Count") int n) { return Map.of("n", n); }
    @POST @Path("form") @Consumes("application/x-www-form-urlencoded")
    public Map<String, Integer> form(@FormParam("n") int n) { return Map.of("n", n); }
    @GET @Path("revision") public Map<String, Integer> revision(@QueryParam("revision") Revision revision) {
      return Map.of("revision", revision.number());
    }
  }

  /** A parameter type with a conversion of its own, whose form the conversion message does not describe. */
  public record Revision(int number) {
    public static Revision valueOf(String value) {
      if (!value.matches("r[0-9]+")) throw new IllegalArgumentException("private parsing detail");
      return new Revision(Integer.parseInt(value.substring(1)));
    }
  }

  @BeforeAll static void start() throws Exception {
    config = Files.createTempFile("cedar-response-contract", ".yml");
    Files.writeString(config, "server:\n  applicationConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\n  adminConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\nlogging:\n  level: WARN\n");
    server = new DropwizardTestSupport<>(TestApplication.class, config.toString());
    server.before();
  }
  @AfterAll static void stop() throws Exception {
    if (server != null) server.after();
    if (config != null) Files.deleteIfExists(config);
  }

  record Case(String path, int status) { public String toString() { return path; } }
  static Stream<Case> errors() {
    List<Case> cases = new ArrayList<>();
    for (int status : new int[]{400,401,403,404,405,406,409,412,415,422,428,429,499,500,502,503,504}) {
      cases.add(new Case("direct/" + status, status));
      cases.add(new Case("framework/" + status, status));
    }
    cases.addAll(List.of(new Case("missing",404), new Case("query?limit=abc",400),
        new Case("authentication",401), new Case("quota/429",429), new Case("quota/503",503), new Case("crash",500), new Case("domain",500)));
    return cases.stream();
  }
  @ParameterizedTest(name="{0}") @MethodSource("errors")
  void nativeErrorsHaveOneWireContract(Case c) throws Exception {
    HttpResponse<String> response = request("GET", c.path(), null, null);
    JsonNode error = error(response, c.status());
    if (c.status() == 401) assertTrue(response.headers().firstValue("WWW-Authenticate").isPresent());
    if (c.path().startsWith("quota/")) {
      assertEquals("writes",error.path("parameters").path("policy").asText());
      assertEquals("7",response.headers().firstValue("Retry-After").orElseThrow());
    }
    if (c.path().startsWith("framework/")) {
      assertEquals("30", response.headers().firstValue("Retry-After").orElse(null));
      assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
    }
    if (c.path().equals("crash") || c.path().equals("domain")) {
      assertTrue(error.path("errorId").isTextual());
      assertDoesNotThrow(() -> UUID.fromString(error.path("errorId").asText()));
    }
  }
  record Conversion(String method, String path, String header, String value, int status, String message) {
    public String toString() { return method + " " + path + (header == null ? "" : " " + header); }
  }
  static Stream<Conversion> conversions() {
    String int32 = "an integer from -2147483648 to 2147483647";
    return Stream.of(
        new Conversion("GET", "query?limit=abc", null, null, 400, "The query parameter limit must be " + int32),
        new Conversion("GET", "query?limit=99999999999", null, null, 400, "The query parameter limit must be " + int32),
        new Conversion("GET", "optional?offset=abc", null, null, 400, "The query parameter offset must be " + int32),
        new Conversion("GET", "each?n=1&n=abc", null, null, 400,
            "Every value of the query parameter n must be an integer from -9223372036854775808 to 9223372036854775807"),
        new Conversion("GET", "revision?revision=abc", null, null, 400,
            "The query parameter revision is not in a form this route accepts"),
        new Conversion("GET", "count/abc", null, null, 404, "The path parameter n must be " + int32),
        new Conversion("GET", "header", "X-Count", "abc", 400, "The header X-Count must be " + int32),
        new Conversion("POST", "form", "Content-Type", "application/x-www-form-urlencoded", 400,
            "The form parameter n must be " + int32));
  }
  /**
   * A parameter Jersey cannot convert is answered in the shared error body with a message that
   * identifies the parameter and says what form it takes. Jersey's text, which describes the Java
   * parsing that failed, stays out of the body, and every other field is the one a bodyless refusal of
   * that status has always carried.
   */
  @ParameterizedTest(name="{0}") @MethodSource("conversions")
  void aParameterThatCannotBeConvertedIsNamedWithTheFormItTakes(Conversion c) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getLocalPort()+"/responses/"+c.path()))
        .timeout(java.time.Duration.ofSeconds(5));
    if (c.header() != null) request.header(c.header(), c.value());
    HttpResponse<String> response = CLIENT.send(request.method(c.method(), c.method().equals("POST")
        ? HttpRequest.BodyPublishers.ofString("n=abc") : HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofString());
    JsonNode error = error(response, c.status());
    assertEquals(c.message(), error.path("message").asText(null), response.body());
    com.fasterxml.jackson.databind.node.ObjectNode expected =
        JSON.valueToTree(CedarResponse.status(c.status()).build().getEntity());
    expected.put("message", c.message());
    assertEquals(expected, error);
    for (String conversionText : List.of("For input string", "NumberFormatException", "abc", "99999999999",
        "private parsing detail", "HTTP 40")) {
      assertFalse(response.body().contains(conversionText), response.body());
    }
  }
  @Test void wrongMethodRetainsAllow() throws Exception {
    HttpResponse<String> response = request("DELETE", "created", null, null);
    error(response,405);
    assertTrue(response.headers().firstValue("Allow").orElse("").contains("GET"));
  }
  @Test void multipleChallengesSurvive() throws Exception {
    HttpResponse<String> response = request("GET", "challenge", null, null);
    error(response,401);
    String challenges = String.join(",", response.headers().allValues("WWW-Authenticate"));
    assertTrue(challenges.contains("Basic realm=\"upstream\""),challenges);
    assertTrue(challenges.contains("Bearer realm=\"cedar\""),challenges);
  }
  @Test void frameworkRequestFailuresUseTheSameEnvelope() throws Exception {
    error(request("POST","input","{broken","application/json"),400);
    error(request("POST","input","{\"name\":\"\"}","application/json"),422);
    error(request("POST","input","name","text/plain"),415);
  }
  @Test void successesAndExplicitOperationReportsKeepTheirContracts() throws Exception {
    HttpResponse<String> empty = request("GET","empty",null,null);
    assertEquals(200,empty.statusCode()); assertEquals("",empty.body());
    HttpResponse<String> noContent = request("GET","no-content",null,null);
    assertEquals(204,noContent.statusCode()); assertEquals("",noContent.body());
    HttpResponse<String> created = request("GET","created",null,null);
    assertEquals(201,created.statusCode()); assertTrue(created.headers().firstValue("Location").orElseThrow().endsWith("/responses/item"));
    assertEquals("\"7\"",created.headers().firstValue("ETag").orElseThrow());
    assertEquals("created",JSON.readTree(created.body()).path("name").asText());
    HttpResponse<String> head = request("HEAD","created",null,null);
    assertEquals("",head.body()); assertEquals("\"7\"",head.headers().firstValue("ETag").orElseThrow());
    HttpResponse<String> explicit = request("GET","explicit",null,null);
    assertEquals(409,explicit.statusCode()); assertEquals("no-store",explicit.headers().firstValue("Cache-Control").orElseThrow());
    assertEquals(Map.of("outcome","blocked"),JSON.readValue(explicit.body(),Map.class));
  }
  private static JsonNode error(HttpResponse<String> response, int status) throws Exception {
    assertEquals(status,response.statusCode(),response.body());
    assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"));
    JsonNode error = JSON.readTree(response.body());
    assertEquals(status,error.path("statusCode").asInt());
    String expectedSymbol = switch (status) {
      case 400 -> "BAD_REQUEST";
      case 401 -> "UNAUTHORIZED";
      case 403 -> "FORBIDDEN";
      case 404 -> "NOT_FOUND";
      case 405 -> "METHOD_NOT_ALLOWED";
      case 406 -> "NOT_ACCEPTABLE";
      case 409 -> "CONFLICT";
      case 412 -> "PRECONDITION_FAILED";
      case 415 -> "UNSUPPORTED_MEDIA_TYPE";
      case 422 -> "UNPROCESSABLE_ENTITY";
      case 428 -> "PRECONDITION_REQUIRED";
      case 429 -> "TOO_MANY_REQUESTS";
      case 499 -> "HTTP_499";
      case 500 -> "INTERNAL_SERVER_ERROR";
      case 502 -> "BAD_GATEWAY";
      case 503 -> "SERVICE_UNAVAILABLE";
      case 504 -> "GATEWAY_TIMEOUT";
      default -> throw new AssertionError("Declare the expected symbolic status for " + status);
    };
    assertEquals(expectedSymbol,error.path("status").asText());
    Set<String> fields = new HashSet<>(); error.fieldNames().forEachRemaining(fields::add);
    assertEquals(ERROR_FIELDS,fields);
    assertFalse(response.body().contains("private backend detail"));
    assertFalse(response.body().contains("stackTrace"));
    return error;
  }
  private static HttpResponse<String> request(String method,String path,String body,String type) throws Exception {
    var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getLocalPort()+"/responses/"+path))
        .timeout(java.time.Duration.ofSeconds(5));
    if(type != null) request.header("Content-Type",type);
    return CLIENT.send(request.method(method,body == null ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
  }
}
