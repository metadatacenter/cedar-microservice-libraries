package org.metadatacenter.server.cache.user;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.security.model.user.CedarUserSummary;
import org.metadatacenter.util.test.CedarTestEnvironment;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How the cache reads a summary from the user server when it does not hold one.
 *
 * <p>A server resolves the display names of a resource's creator, owner and last updater through this
 * lookup. Until the test environment stopped inheriting the shell's addresses, a workstation build
 * reached it only by calling the developer's running user server, so whether a summary was read
 * correctly depended on that server and on the users it happened to hold. Here a stub on a port the
 * operating system assigns answers in each of the shapes the lookup distinguishes.
 */
@Tag("integration")
public class UserSummaryCacheServerLookupTest {

  private static final String KNOWN = "aaaaaaaa-0000-0000-0000-000000000001";
  private static final String UNKNOWN = "aaaaaaaa-0000-0000-0000-000000000002";
  private static final String EMPTY = "aaaaaaaa-0000-0000-0000-000000000003";
  private static final String NO_SCREEN_NAME = "aaaaaaaa-0000-0000-0000-000000000004";
  private static final String USERS = "https://metadatacenter.org/users/";

  private static final List<String> requests = new CopyOnWriteArrayList<>();
  private static HttpServer userServer;
  private static Map<String, String> previousOverride;

  @BeforeAll
  static void startTheUserServer() throws IOException {
    userServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    userServer.createContext("/users/", exchange -> {
      String path = exchange.getRequestURI().getPath();
      requests.add(path + " " + exchange.getRequestHeaders().getFirst("Authorization"));
      String uuid = path.substring("/users/".length(), path.length() - "/summary".length());
      int status = 200;
      String body;
      switch (uuid) {
        case KNOWN -> body = "{\"screenName\":\"Ada Lovelace\"}";
        case EMPTY -> body = "";
        case NO_SCREEN_NAME -> body = "{\"firstName\":\"Ada\"}";
        default -> {
          status = 404;
          body = "{\"errorKey\":\"userNotFound\"}";
        }
      }
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    userServer.start();

    previousOverride = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;
    Map<String, String> environment = CedarTestEnvironment.build();
    environment.put("CEDAR_USER_SERVER_HOST", "127.0.0.1");
    environment.put("CEDAR_USER_HTTP_PORT", String.valueOf(userServer.getAddress().getPort()));
    CedarEnvironmentSource.setOverride(environment);
  }

  @AfterAll
  static void stopTheUserServer() {
    UserSummaryCache.discardForTesting();
    CedarEnvironmentSource.setOverride(previousOverride);
    userServer.stop(0);
  }

  @BeforeEach
  void startFromEmptyCaches() {
    CedarConfig config =
        CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    UserSummaryCache.discardForTesting();
    UserSummaryCache.init(config, TestAuthUtil.getInMemoryUserService(config));
    requests.clear();
  }

  @Test
  void aSummaryTheUserServerHoldsIsReadAndCached() {
    CedarUserSummary summary = UserSummaryCache.getInstance().getUser(USERS + KNOWN);

    assertNotNull(summary);
    assertEquals("Ada Lovelace", summary.getScreenName());
    assertEquals(USERS + KNOWN, summary.getId());

    UserSummaryCache.getInstance().getUser(USERS + KNOWN);
    assertEquals(1, requests.size(), "the second lookup is answered from the cache: " + requests);
  }

  @Test
  void theLookupAsksForTheUuidAsTheAdminUser() {
    UserSummaryCache.getInstance().getUser(USERS + KNOWN);

    assertEquals(1, requests.size(), requests::toString);
    String request = requests.get(0);
    String adminAuthHeader = TestAuthUtil.getAdminUserAuthHeader(
        CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE)));
    assertEquals("/users/" + KNOWN + "/summary " + adminAuthHeader, request);
  }

  @Test
  void aUserTheServerDoesNotKnowHasNoSummary() {
    assertNull(UserSummaryCache.getInstance().getUser(USERS + UNKNOWN));
  }

  @Test
  void anEmptyAnswerHasNoSummary() {
    assertNull(UserSummaryCache.getInstance().getUser(USERS + EMPTY));
  }

  @Test
  void anAnswerWithoutAScreenNameHasNoSummary() {
    assertNull(UserSummaryCache.getInstance().getUser(USERS + NO_SCREEN_NAME));
  }

  @Test
  void anUnresolvableUserIsNotAskedForAgain() {
    UserSummaryCache.getInstance().getUser(USERS + UNKNOWN);
    UserSummaryCache.getInstance().getUser(USERS + UNKNOWN);

    assertEquals(1, requests.size(), "the failed lookup is remembered: " + requests);
  }
}
