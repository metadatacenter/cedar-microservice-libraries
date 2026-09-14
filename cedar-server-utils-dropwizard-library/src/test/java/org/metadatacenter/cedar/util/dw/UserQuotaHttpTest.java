package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.core.Application;
import io.dropwizard.core.Configuration;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.testing.DropwizardTestSupport;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;
import org.metadatacenter.cedar.util.dw.ratelimit.*;
import org.metadatacenter.config.*;
import org.metadatacenter.exception.security.*;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.queue.util.EmbeddedRedis;
import org.metadatacenter.server.security.*;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Files;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Real Jetty/Jersey request injection, authentication, quota admission and exception mapping. */
class UserQuotaHttpTest {
  static int redisPort;
  static AtomicInteger mutations = new AtomicInteger();

  public static class TestApplication extends Application<Configuration> {
    @Override public void run(Configuration config, Environment environment) {
      ObjectMapper mapper = new ObjectMapper();
      RateLimitConfig quotas = mapper.convertValue(Map.of("mode", "enforce",
          "total", Map.of("requestsPerMinute", 1, "burst", 10),
          "reads", Map.of("requestsPerMinute", 1, "burst", 2),
          "writes", Map.of("requestsPerMinute", 1, "burst", 1)), RateLimitConfig.class);
      RedisUserQuotaStore store = new RedisUserQuotaStore(quotas, mapper.convertValue(
          Map.of("host", "127.0.0.1", "port", redisPort), CacheServerConnection.class));
      environment.lifecycle().manage(new io.dropwizard.lifecycle.Managed() {
        @Override public void stop() { store.close(); }
      });
      environment.jersey().register(new UserRateLimitFeature(new UserRateLimits(quotas, store, environment.metrics())));
      environment.jersey().register(new TestResource());
      environment.jersey().register(CedarExceptionMapper.class);
      environment.jersey().register(CedarCedarExceptionMapper.class);
      environment.jersey().register(new InstanceContextInjectionFeature(environment.jersey().getResourceConfig()));
    }
  }

  @Path("/quota-test")
  public static class TestResource extends CedarMicroserviceResource {
    public TestResource() { super(new CedarConfig(), null); }
    @GET public Response read() throws CedarAccessException {
      buildRequestContext();
      return Response.ok(Map.of("mutations", mutations.get())).build();
    }
    @POST public Response write() throws CedarAccessException {
      buildRequestContext();
      buildRequestContext(); // A helper building another context must not charge twice.
      return Response.ok(Map.of("mutations", mutations.incrementAndGet())).build();
    }
    @GET @Path("public") @AnonymousAccess public Response open() {
      buildAnonymousRequestContext();
      return Response.ok(Map.of("public", true)).build();
    }
  }

  @Test void quotaIsAppliedBeforeMutationAndHeaderSpoofingCannotBypassIt() throws Exception {
    var yaml = Files.createTempFile("cedar-quota-http", ".yml");
    Files.writeString(yaml, "server:\n  applicationConnectors:\n    - type: http\n      port: 0\n  adminConnectors:\n    - type: http\n      port: 0\nlogging:\n  level: WARN\n");
    Authorization.setAuthorizationResolver(new IAuthorizationResolver() {
      @Override public CedarUser getUser(LinkedDataUtil util, AuthRequest request, IUserService service) throws CedarAccessException {
        if (request == null || !"user-1".equals(request.getAuthString())) throw new AuthorizationNotFoundException();
        CedarUser user = new CedarUser(); user.setId("stable-user-1"); return user;
      }
      @Override public CedarUser getUserAndEnsurePermission(LinkedDataUtil util, AuthRequest request,
          CedarPermission permission, IUserService service) throws CedarAccessException {
        return getUser(util, request, service);
      }
    });
    try (var redis = EmbeddedRedis.start()) {
      redisPort = redis.port(); mutations.set(0);
      var server = new DropwizardTestSupport<Configuration>(TestApplication.class, yaml.toString());
      try {
        server.before();
        HttpClient client = HttpClient.newHttpClient();
        URI uri = URI.create("http://127.0.0.1:" + server.getLocalPort() + "/quota-test");
        assertEquals(401, client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var first = client.send(HttpRequest.newBuilder(uri).header("Authorization", "apiKey user-1")
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, first.statusCode(), first.body());
        var rejected = client.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer user-1")
            .header(ArtifactServiceConfig.HEADER, "forged-service-key")
            .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(429, rejected.statusCode(), rejected.body());
        assertTrue(Long.parseLong(rejected.headers().firstValue("Retry-After").orElseThrow()) > 0);
        assertEquals("writes", new ObjectMapper().readTree(rejected.body()).path("policy").asText());
        assertEquals(1, mutations.get(), "Rejected POST must never run its mutation");
        var read = client.send(HttpRequest.newBuilder(uri).header("Authorization", "apiKey user-1").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, read.statusCode(), read.body());
        // Anonymous endpoints keep their existing behavior even with an irrelevant credential.
        var open = client.send(HttpRequest.newBuilder(URI.create(uri + "/public"))
            .header("Authorization", "Bearer invalid").GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, open.statusCode(), open.body());
      } finally { server.after(); }
    } finally {
      Files.deleteIfExists(yaml);
      Authorization.setAuthorizationResolver(null);
    }
  }
}
