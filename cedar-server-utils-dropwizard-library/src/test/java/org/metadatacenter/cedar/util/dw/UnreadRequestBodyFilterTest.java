package org.metadatacenter.cedar.util.dw;

import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.annotation.Priority;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("integration")
class UnreadRequestBodyFilterTest {
  private static DropwizardTestSupport<Configuration> server;
  private static java.nio.file.Path config;

  public static class App extends Application<Configuration> {
    @Override public void run(Configuration configuration, Environment environment) {
      CedarMicroserviceApplication.registerHttpConnectionPolicy(environment);
      CedarMicroserviceApplication.registerExceptionMappers(environment);
      environment.jersey().register(new Fixture());
      environment.jersey().register(new Authentication());
      environment.servlets().addFilter("servlet-refusal", (Filter) (request, response, chain) -> {
        if (((HttpServletRequest) request).getRequestURI().equals("/servlet-refusal")) {
          ((HttpServletResponse) response).setStatus(403);
          response.getWriter().write("refused");
          response.flushBuffer();
        } else chain.doFilter(request, response);
      }).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");
      var async = environment.servlets().addServlet("async-refusal", new HttpServlet() {
        @Override protected void service(HttpServletRequest request, HttpServletResponse response) {
          var context = request.startAsync();
          context.start(() -> {
            try {
              response.setStatus(401);
              response.getWriter().write("refused asynchronously");
              response.flushBuffer();
            } catch (IOException e) { throw new UncheckedIOException(e); }
            finally { context.complete(); }
          });
        }
      });
      async.setAsyncSupported(true);
      async.addMapping("/async-refusal");
    }
  }

  @PreMatching @Priority(Priorities.AUTHENTICATION)
  public static class Authentication implements ContainerRequestFilter {
    @Override public void filter(ContainerRequestContext request) {
      if (request.getUriInfo().getPath().equals("auth-refusal")) {
        request.abortWith(Response.status(401).entity("refused").build());
      }
    }
  }

  @Path("/") @Produces("text/plain")
  public static class Fixture {
    @GET @Path("ping") public String ping() { return "pong"; }
    @POST @Path("refuse/{status}") public Response refuse(@PathParam("status") int status) {
      return Response.status(status).entity("refused").build();
    }
    @POST @Path("consume/{status}") @Consumes("application/json")
    public Response consume(@PathParam("status") int status, InputStream input) throws IOException {
      return Response.status(status).entity(new String(input.readAllBytes(), StandardCharsets.UTF_8)).build();
    }
    @POST @Path("partial") public String partial(InputStream input) throws IOException {
      return Integer.toString(input.read());
    }
  }

  @BeforeAll static void start() throws Exception {
    config = Files.createTempFile("cedar-unread-body-", ".yml");
    Files.writeString(config, "server:\n  applicationConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\n  adminConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\nlogging:\n  level: WARN\n");
    server = new DropwizardTestSupport<>(App.class, config.toString());
    server.before();
  }

  @AfterAll static void stop() throws Exception {
    if (server != null) server.after();
    if (config != null) Files.deleteIfExists(config);
  }

  record Refusal(String path, String type, int status) {}
  static Stream<Refusal> refusals() {
    return Stream.of(new Refusal("/refuse/401", "application/json", 401),
        new Refusal("/refuse/403", "application/json", 403),
        new Refusal("/consume/200", "text/plain", 415),
        new Refusal("/missing", "application/json", 404),
        new Refusal("/auth-refusal", "application/json", 401),
        new Refusal("/servlet-refusal", "application/json", 403),
        new Refusal("/async-refusal", "application/json", 401));
  }

  /** Hold the body until after the response: deterministic coverage of the CI timing race. */
  @ParameterizedTest @MethodSource("refusals")
  void anEarlyRefusalAnnouncesClosureWithoutWaitingForTheBody(Refusal refusal) throws Exception {
    try (Wire wire = new Wire()) {
      wire.send("POST " + refusal.path() + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: " +
          refusal.type() + "\r\nContent-Length: 1048576\r\n\r\n");
      Reply reply = wire.read();
      assertEquals(refusal.status(), reply.status);
      assertEquals("close", reply.headers.get("connection"));
      assertEquals(-1, wire.input.read(), "The announced connection must close");
    }
  }

  @Test void anUnfinishedChunkedBodyAlsoAnnouncesClosure() throws Exception {
    try (Wire wire = new Wire()) {
      wire.send("POST /refuse/403 HTTP/1.1\r\nHost: localhost\r\nTransfer-Encoding: chunked\r\n\r\n");
      Reply reply = wire.read();
      assertEquals(403, reply.status);
      assertEquals("close", reply.headers.get("connection"));
      assertEquals(-1, wire.input.read());
    }
  }

  @Test void aPartiallyConsumedSuccessfulRequestAlsoAnnouncesClosure() throws Exception {
    try (Wire wire = new Wire()) {
      wire.send("POST /partial HTTP/1.1\r\nHost: localhost\r\nContent-Length: 10\r\n\r\nx");
      Reply reply = wire.read();
      assertEquals(200, reply.status);
      assertEquals("120", reply.body);
      assertEquals("close", reply.headers.get("connection"));
    }
  }

  @ParameterizedTest @ValueSource(ints = {200, 400})
  void aConsumedBodyKeepsTheConnectionUsableEvenForAnError(int status) throws Exception {
    for (boolean chunked : new boolean[]{false, true}) {
      try (Wire wire = new Wire()) {
        wire.send("POST /consume/" + status + " HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" +
            (chunked ? "Transfer-Encoding: chunked\r\n\r\n2\r\n{}\r\n0\r\n\r\n" : "Content-Length: 2\r\n\r\n{}"));
        Reply reply = wire.read();
        assertEquals(status, reply.status);
        assertEquals("{}", reply.body);
        assertNotEquals("close", reply.headers.get("connection"));
        wire.send("GET /ping HTTP/1.1\r\nHost: localhost\r\n\r\n");
        assertEquals("pong", wire.read().body);
      }
    }
  }

  @Test void bodylessRequestsStillReuseTheConnection() throws Exception {
    try (Wire wire = new Wire()) {
      for (int i = 0; i < 3; i++) {
        wire.send("GET /ping HTTP/1.1\r\nHost: localhost\r\n\r\n");
        Reply reply = wire.read();
        assertEquals(200, reply.status);
        assertNotEquals("close", reply.headers.get("connection"));
      }
    }
  }

  @Test void continueDoesNotDiscardInputOrCloseARequestThatWillBeConsumed() throws Exception {
    try (Wire wire = new Wire()) {
      wire.send("POST /consume/200 HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n" +
          "Content-Length: 2\r\nExpect: 100-continue\r\n\r\n");
      Reply interim = wire.read();
      assertEquals(100, interim.status);
      assertNotEquals("close", interim.headers.get("connection"));
      wire.send("{}");
      Reply reply = wire.read();
      assertEquals(200, reply.status);
      assertEquals("{}", reply.body);
      assertNotEquals("close", reply.headers.get("connection"));
      wire.send("GET /ping HTTP/1.1\r\nHost: localhost\r\n\r\n");
      assertEquals("pong", wire.read().body);
    }
  }

  @Test void http2DoesNotInstallAnHttp1ConnectionHeaderHook() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var chain = mock(FilterChain.class);
    when(request.getProtocol()).thenReturn("HTTP/2.0");
    new UnreadRequestBodyFilter().doFilter(request, response, chain);
    verify(chain).doFilter(request, response);
    verifyNoInteractions(response);
    verify(request, never()).setAttribute(anyString(), any());
  }

  record Reply(int status, Map<String, String> headers, String body) {}

  /** Minimal framed HTTP/1 reader so the test never retries or silently opens another connection. */
  static final class Wire implements AutoCloseable {
    final Socket socket = new Socket("127.0.0.1", server.getLocalPort());
    final InputStream input = new BufferedInputStream(socket.getInputStream());
    Wire() throws IOException { socket.setSoTimeout(3000); }
    void send(String text) throws IOException {
      socket.getOutputStream().write(text.getBytes(StandardCharsets.UTF_8));
      socket.getOutputStream().flush();
    }
    String line() throws IOException {
      var bytes = new ByteArrayOutputStream();
      for (int c; (c = input.read()) != -1;) {
        if (c == '\n') return bytes.toString(StandardCharsets.US_ASCII).replaceFirst("\r$", "");
        bytes.write(c);
      }
      throw new EOFException("No complete HTTP line: " + bytes);
    }
    Reply read() throws IOException {
      int status = Integer.parseInt(line().split(" ")[1]);
      Map<String, String> headers = new HashMap<>();
      for (String line; !(line = line()).isEmpty();) {
        int colon = line.indexOf(':');
        headers.put(line.substring(0, colon).toLowerCase(Locale.ROOT), line.substring(colon + 1).trim());
      }
      var body = new ByteArrayOutputStream();
      if ("chunked".equalsIgnoreCase(headers.get("transfer-encoding"))) {
        for (int size; (size = Integer.parseInt(line().split(";", 2)[0], 16)) != 0;) {
          body.write(exact(size));
          assertEquals("", line());
        }
        while (!line().isEmpty()) { /* trailers */ }
      } else if (headers.containsKey("content-length")) {
        body.write(exact(Integer.parseInt(headers.get("content-length"))));
      } else if (status >= 200 && status != 204 && status != 304) {
        body.write(input.readAllBytes());
      }
      return new Reply(status, headers, body.toString(StandardCharsets.UTF_8));
    }
    byte[] exact(int count) throws IOException {
      byte[] bytes = input.readNBytes(count);
      if (bytes.length != count) throw new EOFException("Truncated response body");
      return bytes;
    }
    @Override public void close() throws IOException { socket.close(); }
  }
}
