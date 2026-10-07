package org.metadatacenter.cedar.util.dw;

import com.sun.net.httpserver.HttpServer;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.apache.hc.client5.http.fluent.Request;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.metadatacenter.util.http.HttpTimeouts;
import org.metadatacenter.util.http.ResponseRelay;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;
import static org.junit.jupiter.api.Assertions.*;

/** A real HTTP peer, the production buffered client/relay, then Jetty serialization. */
class ResponseRelayMatrixTest {
  static HttpServer peer; static DropwizardTestSupport<Configuration> server; static java.nio.file.Path config;
  static final AtomicInteger calls = new AtomicInteger();
  static final byte[] BODY = "{\"message\":\"upstream answer with enough bytes to compress\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
  static final HttpClient CLIENT = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  public static class App extends Application<Configuration> {
    @Override public void run(Configuration config,Environment env) { env.jersey().register(new Relay()); }
  }
  @jakarta.ws.rs.Path("/relay") public static class Relay {
    @GET @jakarta.ws.rs.Path("{status}/{gzip}/{anonymous}")
    public Response get(@PathParam("status") int status,@PathParam("gzip") boolean gzip,@PathParam("anonymous") boolean anonymous) throws Exception {
      try(var upstream=HttpTimeouts.NO_REDIRECT_INTERACTIVE.execute(Request.get("http://127.0.0.1:"+peer.getAddress().getPort()+"/"+status+"/"+gzip))) {
        var response=ResponseRelay.responseBuilder(upstream);
        if(anonymous) response.header("Cache-Control",null).header("Cache-Control","no-store");
        return response.build();
      }
    }
  }
  @BeforeAll static void start() throws Exception {
    peer=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    peer.createContext("/",e->{ calls.incrementAndGet();var path=e.getRequestURI().getPath().split("/");int status=Integer.parseInt(path[1]);
      var h=e.getResponseHeaders(); h.add("Content-Type","application/json");h.add("ETag","\"7\"");
      h.add("Vary","Accept");h.add("Vary","Origin");h.add("Cache-Control","private, max-age=0");
      h.add("Retry-After","17");h.add("Allow","GET, HEAD");h.add("Location","/target");
      h.add("WWW-Authenticate","Bearer realm=\"cedar\"");h.add("WWW-Authenticate","Basic realm=\"legacy\"");
      h.add("Set-Cookie","private=upstream"); h.add("X-Upstream-Private","omit");
      byte[] body=BODY;
      if(Boolean.parseBoolean(path[2])) {var bytes=new ByteArrayOutputStream();try(var zip=new GZIPOutputStream(bytes)){zip.write(body);}body=bytes.toByteArray();h.add("Content-Encoding","gzip");}
      if(status==204||status==205||status==304) e.sendResponseHeaders(status,-1);
      else {e.sendResponseHeaders(status,body.length);e.getResponseBody().write(body);} e.close();
    });peer.start();
    config=Files.createTempFile("relay-matrix-",".yml");
    Files.writeString(config,"server:\n  applicationConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\n  adminConnectors:\n    - type: http\n      bindHost: 127.0.0.1\n      port: 0\nlogging:\n  level: WARN\n");
    server=new DropwizardTestSupport<>(App.class,config.toString());server.before();
  }
  @AfterAll static void stop() throws Exception {if(server!=null)server.after();if(peer!=null)peer.stop(0);if(config!=null)Files.deleteIfExists(config);}
  @Test void headRetainsMetadataWithoutSendingTheBody() throws Exception {
    int before = calls.get();
    var response = CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.getLocalPort()
        + "/relay/200/true/false")).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
        HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(200, response.statusCode());
    assertEquals(0, response.body().length);
    assertEquals("\"7\"", response.headers().firstValue("ETag").orElseThrow());
    assertEquals("17", response.headers().firstValue("Retry-After").orElseThrow());
    assertEquals(before + 1, calls.get());
  }
  static Stream<Arguments> replies() {return Stream.of(200,201,204,205,302,304,401,405,412,429,499,503,504).flatMap(status->Stream.of(false,true).flatMap(gzip->Stream.of(false,true).map(anonymous->Arguments.of(status,gzip,anonymous))));}
  @ParameterizedTest(name="{0}, gzip={1}, anonymous={2}") @MethodSource("replies")
  void preservesTheWireContract(int status,boolean gzip,boolean anonymous) throws Exception {
    int before=calls.get();
    var r=CLIENT.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getLocalPort()+"/relay/"+status+"/"+gzip+"/"+anonymous)).header("Accept-Encoding","identity").GET().build(),HttpResponse.BodyHandlers.ofByteArray());
    assertEquals(status,r.statusCode());assertEquals(before+1,calls.get());
    assertArrayEquals(Set.of(204,205,304).contains(status)?new byte[0]:BODY,r.body());
    assertEquals("17",r.headers().firstValue("Retry-After").orElseThrow());
    assertEquals("GET, HEAD",r.headers().firstValue("Allow").orElseThrow());
    assertTrue(r.headers().firstValue("Location").orElseThrow().endsWith("/target"));
    assertEquals(Set.of("Bearer realm=\"cedar\"","Basic realm=\"legacy\""),new HashSet<>(r.headers().allValues("WWW-Authenticate")));
    String vary=String.join(",",r.headers().allValues("Vary"));assertTrue(vary.contains("Accept"));assertTrue(vary.contains("Origin"));
    assertEquals(anonymous?"no-store":"private, max-age=0",r.headers().firstValue("Cache-Control").orElseThrow());
    assertTrue(r.headers().firstValue("Set-Cookie").isEmpty());assertTrue(r.headers().firstValue("Content-Encoding").isEmpty());assertTrue(r.headers().firstValue("X-Upstream-Private").isEmpty());
  }
}
