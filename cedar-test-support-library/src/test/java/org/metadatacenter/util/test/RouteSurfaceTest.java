package org.metadatacenter.util.test;

import com.sun.net.httpserver.HttpServer;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.ToIntFunction;

/**
 * The route safety net the server suites run over their whole declared surface. A defect here passes
 * every one of those suites without probing anything: a route missed by reflection is never asked,
 * and a probe that does not reach the route measures something else. So the surface it reads, the
 * requests it sends and the divergences it reports are asserted against a stub whose answers are
 * known.
 */
@Tag("integration")
public class RouteSurfaceTest {

  @Path("/widgets")
  public abstract static class AbstractWidgetResource {
    @GET
    @Path("/{id}")
    public void read() {
    }
  }

  @Path("/widgets/")
  @Consumes("application/json")
  public static class WidgetResource extends AbstractWidgetResource {
    @POST
    public void create() {
    }

    @PUT
    @Path("{id}")
    @Consumes("multipart/form-data")
    public void upload() {
    }

    @DELETE
    @Path("/{id}")
    public void delete() {
    }

    public void notARoute() {
    }
  }

  @Path("/servers/{server}/report")
  public static class ReportResource {
    @GET
    public void report() {
    }
  }

  /** What a Jersey {@code Resource} exposes: the handler classes and instances registered under it. */
  public static final class RegisteredResource {
    public List<Class<?>> getHandlerClasses() {
      return List.of(ReportResource.class);
    }

    public List<Object> getHandlerInstances() {
      return List.of(new WidgetResource());
    }
  }

  @Test
  public void everyDeclaredRouteIsFoundIncludingInheritedOnes() {
    List<RouteSurface.Endpoint> endpoints = RouteSurface.endpoints(WidgetResource.class);

    Assertions.assertEquals(
        List.of("DELETE /widgets/{id}", "GET /widgets/{id}", "POST /widgets", "PUT /widgets/{id}"),
        endpoints.stream().map(RouteSurface.Endpoint::key).toList());
    Assertions.assertEquals("AbstractWidgetResource", endpoint(endpoints, "GET").declaringClass);
    Assertions.assertEquals("application/json", endpoint(endpoints, "POST").consumes,
        "a route without its own @Consumes takes its class's");
    Assertions.assertEquals("multipart/form-data", endpoint(endpoints, "PUT").consumes,
        "a route's own @Consumes overrides its class's");
  }

  @Test
  public void theResourcesAnApplicationRegisteredAreDiscoveredThroughTheirHandlers() {
    List<Class<?>> discovered = RouteSurface.registeredResourceClasses(
        List.of(WidgetResource.class, new RegisteredResource(), String.class, RouteSurfaceTest.class),
        "org.metadatacenter.util.test");

    Assertions.assertEquals(List.of(ReportResource.class, WidgetResource.class), discovered,
        "registered resources, and those registered through a Jersey resource's handlers, are probed; "
            + "classes outside the package or without @Path are not");
  }

  @Test
  public void eachProbeReachesTheRouteItNamesWithTheMediaTypeItConsumes() throws Exception {
    Map<String, String> seen = new ConcurrentHashMap<>();
    try (Stub stub = new Stub(exchange -> {
      String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
      seen.put(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath(),
          contentType == null ? "" : contentType);
      return 401;
    })) {
      RouteSurface.assertEveryRouteAnswers(stub.baseUrl(),
          RouteSurface.endpoints(WidgetResource.class, ReportResource.class), 401);
    }

    Assertions.assertEquals(5, seen.size(), seen.toString());
    seen.keySet().forEach(route -> Assertions.assertFalse(route.contains("{"), "an unsubstituted parameter: " + route));
    Assertions.assertEquals("", seen.get("GET /servers/artifact/report"),
        "a server name is drawn from the vocabulary of server names, not made up");
    Assertions.assertEquals("application/json", contentType(seen, "POST"));
    Assertions.assertTrue(contentType(seen, "PUT").startsWith("multipart/form-data; boundary="), contentType(seen, "PUT"));
  }

  @Test
  public void aRouteThatVanishedChangedVerbOrAnswersOtherwiseFailsTheProbe() throws Exception {
    Map<String, Integer> answers = Map.of("GET", 401, "POST", 200, "PUT", 405, "DELETE", 404);
    AssertionFailedError failure;
    try (Stub stub = new Stub(exchange -> answers.get(exchange.getRequestMethod()))) {
      failure = Assertions.assertThrows(AssertionFailedError.class, () ->
          RouteSurface.assertEveryRouteAnswers(stub.baseUrl(), RouteSurface.endpoints(WidgetResource.class), 401));
    }

    String message = failure.getMessage();
    Assertions.assertTrue(message.contains("DELETE /widgets/{id}: got 404 - the route vanished or changed verb"), message);
    Assertions.assertTrue(message.contains("PUT /widgets/{id}: got 405 - the route vanished or changed verb"), message);
    Assertions.assertTrue(message.contains("POST /widgets: expected 401 but got 200"), message);
    Assertions.assertFalse(message.contains("GET /widgets"), message);
    Assertions.assertEquals(3, failure.getActual().getValue(), "one divergence per diverging route");
  }

  @Test
  public void aSurfaceThatAnswersAsExpectedPassesAndAnEmptyOneFails() throws Exception {
    try (Stub stub = new Stub(exchange -> 401)) {
      RouteSurface.assertEveryRouteAnswers(stub.baseUrl(), RouteSurface.endpoints(WidgetResource.class), 401);

      Assertions.assertThrows(AssertionFailedError.class,
          () -> RouteSurface.assertEveryRouteAnswers(stub.baseUrl(), List.of(), 401),
          "a resource list that yields no endpoints would otherwise pass without probing anything");
    }
  }

  private static RouteSurface.Endpoint endpoint(List<RouteSurface.Endpoint> endpoints, String verb) {
    return endpoints.stream().filter(e -> e.verb.equals(verb)).findFirst().orElseThrow();
  }

  private static String contentType(Map<String, String> seen, String verb) {
    return seen.entrySet().stream().filter(e -> e.getKey().startsWith(verb + " ")).findFirst().orElseThrow().getValue();
  }

  /** A loopback server that answers every request with the status the test chooses. */
  private static final class Stub implements AutoCloseable {
    private final HttpServer server;

    Stub(ToIntFunction<com.sun.net.httpserver.HttpExchange> status) throws Exception {
      server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
      server.createContext("/", exchange -> {
        exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(status.applyAsInt(exchange), -1);
        exchange.close();
      });
      server.start();
    }

    String baseUrl() {
      return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }
}
