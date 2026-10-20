package org.metadatacenter.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.rotation.HardcodedPublicKeyLocator;
import org.keycloak.jose.jws.JWSBuilder;
import org.keycloak.representations.AccessToken;
import org.metadatacenter.config.KeycloakConfig;
import org.metadatacenter.config.LinkedDataConfig;
import org.metadatacenter.exception.security.AccessTokenExpiredException;
import org.metadatacenter.exception.security.AccessTokenMissingException;
import org.metadatacenter.exception.security.AuthorizationNotFoundException;
import org.metadatacenter.exception.security.CedarUserNotFoundException;
import org.metadatacenter.exception.security.InvalidOfflineAccessTokenException;
import org.metadatacenter.exception.security.PermissionNotOwnedException;
import org.metadatacenter.exception.security.UnknownAuthorizationTypeException;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserAuthSource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Proxy;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * A request identifies its caller by a bearer token the realm signed or by an API key. A token is honoured only when
 * the realm's key signed it, the realm issued it and it is still active. An expired token asks the client to refresh
 * it; any other bad token is invalid. Either way the caller must be a known user, and an operation then requires the
 * user to hold its permission.
 */
@Tag("integration")
class AuthorizationKeycloakAndApiKeyResolverTest {

  private static final String USERS_BASE = "https://metadatacenter.org/users/";
  private static final String UUID = "11111111-2222-3333-4444-555555555555";
  private static final String API_KEY = "the-api-key";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private KeyPair realmKey;
  private KeycloakDeployment deployment;
  private LinkedDataUtil linkedDataUtil;
  private AuthorizationKeycloakAndApiKeyResolver resolver;
  private CedarUser knownUser;
  private IUserService userService;
  private HttpServer keycloak;

  @BeforeEach
  void setUp() throws Exception {
    realmKey = keyPair();
    // The deployment learns the realm's issuer from its discovery document, so a Keycloak answers for that alone.
    keycloak = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    String base = "http://127.0.0.1:" + keycloak.getAddress().getPort();
    String realm = base + "/realms/CEDAR";
    keycloak.createContext("/realms/CEDAR/.well-known/openid-configuration", exchange -> {
      byte[] body = """
          {"issuer": "%1$s", "authorization_endpoint": "%1$s/protocol/openid-connect/auth",
           "token_endpoint": "%1$s/protocol/openid-connect/token",
           "end_session_endpoint": "%1$s/protocol/openid-connect/logout",
           "jwks_uri": "%1$s/protocol/openid-connect/certs"}
          """.formatted(realm).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    });
    keycloak.start();
    deployment = new KeycloakDeploymentProvider().buildDeployment(objectMapper.readValue("""
        {"realm": "CEDAR", "authServerUrl": "%s", "sslRequired": "external",
         "resource": "cedar-angular-app", "publicClient": true}
        """.formatted(base), KeycloakConfig.class));
    deployment.setPublicKeyLocator(new HardcodedPublicKeyLocator(realmKey.getPublic()));
    linkedDataUtil = new LinkedDataUtil(objectMapper.readValue("""
        {"base": "https://repo.metadatacenter.orgx/", "usersBase": "%s"}
        """.formatted(USERS_BASE), LinkedDataConfig.class));
    resolver = new AuthorizationKeycloakAndApiKeyResolver(deployment);
    knownUser = new CedarUser();
    knownUser.setId(USERS_BASE + UUID);
    knownUser.setPermissions(List.of(CedarPermission.TEMPLATE_READ.getPermissionName()));
    userService = users(Map.of(USERS_BASE + UUID, knownUser));
  }

  @AfterEach
  void stopKeycloak() {
    keycloak.stop(0);
  }

  @Test
  void aTokenTheRealmSignedIdentifiesItsUser() throws Exception {
    CedarUser user = resolver.getUser(linkedDataUtil, bearer(signed(token(), realmKey)), userService);

    assertSame(knownUser, user);
    assertEquals(CedarUserAuthSource.TOKEN, user.getAuthSource());
  }

  @Test
  void anExpiredTokenAsksTheClientToRefreshIt() throws Exception {
    long expiredAt = Instant.now().minusSeconds(60).getEpochSecond();
    AccessToken token = token();
    token.exp(expiredAt);

    AccessTokenExpiredException expired = assertThrows(AccessTokenExpiredException.class,
        () -> resolver.getUser(linkedDataUtil, bearer(signed(token, realmKey)), userService));

    assertEquals(expiredAt, expired.getExpiration());
  }

  @Test
  void aTokenTheRealmDidNotSignOrIssueIsInvalid() throws Exception {
    AccessToken otherRealm = token();
    otherRealm.issuer(deployment.getRealmInfoUrl().replace("/realms/CEDAR", "/realms/OTHER"));
    for (String token : List.of(signed(token(), keyPair()), signed(otherRealm, realmKey),
        new JWSBuilder().jsonContent(token()).none(), "not-a-token")) {
      assertThrows(InvalidOfflineAccessTokenException.class,
          () -> resolver.getUser(linkedDataUtil, bearer(token), userService), token);
    }
  }

  @Test
  void aBearerRequestWithoutATokenIsRefused() {
    assertThrows(AccessTokenMissingException.class,
        () -> resolver.getUser(linkedDataUtil, new CedarBearerAuthRequest(null), userService));
  }

  @Test
  void aValidTokenForAnUnknownOrUnloadableUserFindsNoUser() throws Exception {
    String token = signed(token(), realmKey);
    assertThrows(CedarUserNotFoundException.class,
        () -> resolver.getUser(linkedDataUtil, bearer(token), users(Map.of())));
    assertThrows(CedarUserNotFoundException.class,
        () -> resolver.getUser(linkedDataUtil, bearer(token), failingUsers()));
  }

  @Test
  void anApiKeyIdentifiesItsUser() throws Exception {
    CedarUser user = resolver.getUser(linkedDataUtil, request("apiKey " + API_KEY), userService);

    assertSame(knownUser, user);
    assertEquals(CedarUserAuthSource.API_KEY, user.getAuthSource());
  }

  @Test
  void anUnknownOrUnloadableApiKeyFindsNoUser() {
    assertThrows(CedarUserNotFoundException.class,
        () -> resolver.getUser(linkedDataUtil, request("apiKey another-key"), userService));
    CedarUserNotFoundException failed = assertThrows(CedarUserNotFoundException.class,
        () -> resolver.getUser(linkedDataUtil, request("apiKey " + API_KEY), failingUsers()));
    assertInstanceOf(IOException.class, failed.getCause().getCause(), "the load failure is kept as the cause");
  }

  @Test
  void anUnknownSchemeOrNoAuthorizationIsRefused() {
    assertThrows(UnknownAuthorizationTypeException.class,
        () -> resolver.getUser(linkedDataUtil, request("Basic dXNlcjpwYXNz"), userService));
    assertThrows(AuthorizationNotFoundException.class,
        () -> resolver.getUser(linkedDataUtil, request(null), userService));
  }

  @Test
  void anOperationRequiresItsPermission() throws Exception {
    AuthRequest byKey = request("apiKey " + API_KEY);

    assertSame(knownUser, resolver.getUserAndEnsurePermission(linkedDataUtil, byKey, CedarPermission.TEMPLATE_READ,
        userService));
    assertThrows(PermissionNotOwnedException.class, () -> resolver.getUserAndEnsurePermission(linkedDataUtil, byKey,
        CedarPermission.TEMPLATE_DELETE, userService));
    knownUser.setPermissions(List.of());
    assertThrows(PermissionNotOwnedException.class, () -> resolver.getUserAndEnsurePermission(linkedDataUtil, byKey,
        CedarPermission.TEMPLATE_READ, userService));
  }

  /** An access token for the known user, issued by the deployment's realm and active for five minutes. */
  private AccessToken token() {
    AccessToken token = new AccessToken();
    token.subject(UUID);
    token.issuer(deployment.getRealmInfoUrl());
    token.type("Bearer");
    token.issuedNow();
    token.exp(Instant.now().plusSeconds(300).getEpochSecond());
    return token;
  }

  private static String signed(AccessToken token, KeyPair key) {
    return new JWSBuilder().kid("realm-key").jsonContent(token).rsa256(key.getPrivate());
  }

  private static KeyPair keyPair() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    return generator.generateKeyPair();
  }

  private static AuthRequest bearer(String token) {
    return request("Bearer " + token);
  }

  /** The authorization a request with the given Authorization header carries. */
  private static AuthRequest request(String authorization) {
    HttpServletRequest request = (HttpServletRequest) Proxy.newProxyInstance(
        AuthorizationKeycloakAndApiKeyResolverTest.class.getClassLoader(), new Class<?>[]{HttpServletRequest.class},
        (proxy, method, args) -> method.getName().equals("getHeader") && "Authorization".equalsIgnoreCase(
            (String) args[0]) ? authorization : null);
    return CedarAuthFromRequestFactory.fromRequest(request);
  }

  /** A user service that knows the given users by identifier, and each by the one API key. */
  private static IUserService users(Map<String, CedarUser> byId) {
    return new IUserService() {
      @Override
      public CedarUser findUserByApiKey(String apiKey) {
        return API_KEY.equals(apiKey) ? byId.values().stream().findFirst().orElse(null) : null;
      }

      @Override
      public CedarUser findUser(CedarUserId id) {
        return byId.get(id.getId());
      }
    };
  }

  private static IUserService failingUsers() {
    return new IUserService() {
      @Override
      public CedarUser findUserByApiKey(String apiKey) throws IOException {
        throw new IOException("the user store is unreachable");
      }

      @Override
      public CedarUser findUser(CedarUserId id) throws IOException {
        throw new IOException("the user store is unreachable");
      }
    };
  }
}
