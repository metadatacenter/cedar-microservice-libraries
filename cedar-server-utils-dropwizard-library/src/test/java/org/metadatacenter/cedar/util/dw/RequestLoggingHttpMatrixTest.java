package org.metadatacenter.cedar.util.dw;

import io.dropwizard.core.*;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.annotation.Priority;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.container.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.metadatacenter.server.logging.*;
import org.metadatacenter.server.logging.filter.*;
import org.metadatacenter.server.logging.model.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.metadatacenter.constant.CedarHeaderParameters.*;

class RequestLoggingHttpMatrixTest {
  static DropwizardTestSupport<Configuration> server;
  static java.nio.file.Path config;
  static final Queue<AppLogMessage> events = new ConcurrentLinkedQueue<>();
  static final Map<String,CompletableFuture<Boolean>> cleanup = new ConcurrentHashMap<>();
  static final HttpClient CLIENT = HttpClient.newHttpClient();
  static final CountDownLatch concurrent = new CountDownLatch(2);
  static AppLoggerQueueService previousQueue;
  static String previousSuppression;

  public static class App extends Application<Configuration> {
    @Override public void run(Configuration c, Environment env) {
      CedarMicroserviceApplication.registerExceptionMappers(env);
      CedarMicroserviceApplication.registerRequestLogging(env);
      env.jersey().register(new Fixture());
      env.jersey().register(new EarlyAuthentication());
      // Runs outside the production scope and checks the worker after every dispatch, even OPTIONS/404.
      env.servlets().addFilter("probe",(Filter)(request,response,chain) -> {
        String key = ((HttpServletRequest)request).getHeader("X-Probe-Key");
        ThreadLocalRequestIdHolder.setLoggingContext(new LoggingContext("previous-request","previous-local"));
        try { chain.doFilter(request,response); }
        finally {
          var done = cleanup.get(key);
          if (done != null) done.complete(ThreadLocalRequestIdHolder.getLoggingContext() == null);
          ThreadLocalRequestIdHolder.setLoggingContext(null);
        }
      }).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST),false,"/*");
    }
  }
  @PreMatching @Priority(Priorities.AUTHENTICATION)
  public static class EarlyAuthentication implements ContainerRequestFilter {
    @Override public void filter(ContainerRequestContext request) {
      if (request.getUriInfo().getPath().equals("fixture/auth")) {
        assertNotNull(ThreadLocalRequestIdHolder.getLoggingContext());
        request.abortWith(Response.status(401).build());
      }
    }
  }
  @Path("/fixture") public static class Fixture {
    @GET @Path("ok") public String ok() { return identity(); }
    @GET @Path("crash") public String crash() { assertNotNull(ThreadLocalRequestIdHolder.getLoggingContext()); throw new IllegalStateException("fixture"); }
    @GET @Path("forbidden") public String forbidden() { throw new ForbiddenException(); }
    @GET @Path("concurrent") public String concurrent() throws Exception {
      String before = identity(); concurrent.countDown(); assertTrue(concurrent.await(5,TimeUnit.SECONDS));
      assertEquals(before,identity()); return before;
    }
    private static String identity() {
      LoggingContext c = ThreadLocalRequestIdHolder.getLoggingContext();
      assertNotNull(c); return c.getGlobalRequestId()+"/"+c.getLocalRequestId();
    }
  }
  @BeforeAll static void start() throws Exception {
    previousQueue = AppLogger.appLoggerQueueService;
    previousSuppression = System.getProperty("cedar.test.suppressAppLogQueue");
    var queue = mock(AppLoggerQueueService.class);
    doAnswer(call -> { events.add(call.getArgument(0)); return null; }).when(queue).enqueueEvent(any());
    AppLogger.appLoggerQueueService = queue;
    System.setProperty("cedar.test.suppressAppLogQueue","false");
    config = Files.createTempFile("logging-matrix-",".yml");
    Files.writeString(config,"server:\n  applicationConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\n  adminConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\nlogging:\n  level: WARN\n");
    server = new DropwizardTestSupport<>(App.class,config.toString()); server.before();
  }
  @AfterAll static void stop() throws Exception {
    try { if (server != null) server.after(); if(config != null) Files.deleteIfExists(config); }
    finally {
      AppLogger.appLoggerQueueService = previousQueue;
      if(previousSuppression==null) System.clearProperty("cedar.test.suppressAppLogQueue");
      else System.setProperty("cedar.test.suppressAppLogQueue",previousSuppression);
    }
  }
  static Stream<Arguments> replies() {
    return Stream.of(false,true).flatMap(incoming -> Stream.of(
        Arguments.of("GET","ok",200,incoming), Arguments.of("HEAD","ok",200,incoming),
        Arguments.of("GET","auth",401,incoming), Arguments.of("GET","forbidden",403,incoming),
        Arguments.of("GET","missing",404,incoming), Arguments.of("POST","ok",405,incoming),
        Arguments.of("GET","crash",500,incoming), Arguments.of("OPTIONS","ok",200,incoming)));
  }
  static HttpResponse<String> request(String method, String path, String global) throws Exception {
    String key = UUID.randomUUID().toString(); var done = new CompletableFuture<Boolean>(); cleanup.put(key,done);
    try {
      var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getLocalPort()+"/fixture/"+path))
          .header("X-Probe-Key",key).header(LOCAL_REQUEST_ID_KEY,"untrusted-local")
          .method(method,HttpRequest.BodyPublishers.noBody());
      if(global != null) builder.header(GLOBAL_REQUEST_ID_KEY,global);
      var response = CLIENT.send(builder.build(),HttpResponse.BodyHandlers.ofString());
      assertTrue(done.get(5,TimeUnit.SECONDS),"Worker retained request context");
      return response;
    } finally { cleanup.remove(key); }
  }
  @ParameterizedTest(name="{0} {1}, status={2}, supplied-id={3}") @MethodSource("replies")
  void logsPairedIdsAndCleansTheWorker(String method,String path,int status,boolean incoming) throws Exception {
    events.clear(); String global = incoming ? UUID.randomUUID().toString() : null;
    var response = request(method,path,global); assertEquals(status,response.statusCode(),response.body());
    var logs = events.stream().filter(e -> e.getType()==AppLogType.REQUEST_FILTER).toList();
    if(method.equals("OPTIONS")) { assertTrue(logs.isEmpty()); return; }
    assertEquals(2,logs.size()); var start=logs.get(0);var end=logs.get(1);
    assertEquals(AppLogSubType.START,start.getSubType()); assertEquals(AppLogSubType.END,end.getSubType());
    assertEquals(start.getGlobalRequestId(),end.getGlobalRequestId());
    assertEquals(start.getLocalRequestId(),end.getLocalRequestId());
    assertNotEquals("untrusted-local",start.getLocalRequestId());
    assertNotEquals("previous-request",start.getGlobalRequestId());
    UUID.fromString(start.getLocalRequestId());
    if(incoming) assertEquals(global,start.getGlobalRequestId()); else UUID.fromString(start.getGlobalRequestId());
    assertEquals(status,end.getParamAsInt(AppLogParam.STATUS));
  }
  @Test void simultaneousRequestsKeepSeparateIds() throws Exception {
    events.clear(); var workers=Executors.newFixedThreadPool(2);
    try {
      var one=workers.submit(() -> request("GET","concurrent","one"));
      var two=workers.submit(() -> request("GET","concurrent","two"));
      assertTrue(one.get(10,TimeUnit.SECONDS).body().startsWith("one/"));
      assertTrue(two.get(10,TimeUnit.SECONDS).body().startsWith("two/"));
      var logs=events.stream().filter(e->e.getType()==AppLogType.REQUEST_FILTER).toList();
      assertEquals(4,logs.size());
      for(String global:List.of("one","two")) {
        var pair=logs.stream().filter(e->global.equals(e.getGlobalRequestId())).toList();
        assertEquals(2,pair.size()); assertEquals(pair.get(0).getLocalRequestId(),pair.get(1).getLocalRequestId());
      }
    } finally { workers.shutdownNow(); }
  }
}
