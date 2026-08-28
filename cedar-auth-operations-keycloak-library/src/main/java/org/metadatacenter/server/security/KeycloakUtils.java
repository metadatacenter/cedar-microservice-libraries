package org.metadatacenter.server.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.jakarta.rs.json.JacksonJsonProvider;
import com.fasterxml.jackson.jakarta.rs.json.JacksonXmlBindJsonProvider;
import jakarta.ws.rs.client.Client;
import org.jboss.resteasy.client.jaxrs.internal.ResteasyClientBuilderImpl;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.rotation.AdapterTokenVerifier;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.common.VerificationException;
import org.keycloak.common.util.Base64Url;
import org.keycloak.exceptions.TokenNotActiveException;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.JsonWebToken;
import org.keycloak.util.JsonSerialization;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.AccessTokenExpiredException;
import org.metadatacenter.exception.security.AccessTokenMissingException;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.exception.security.InvalidOfflineAccessTokenException;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.AuthRequest;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.cert.X509Certificate;

public class KeycloakUtils {

  private static final Logger log = LoggerFactory.getLogger(KeycloakUtils.class);

  public static <T> T parseToken(String encoded, Class<T> clazz) throws IOException {
    if (encoded == null) {
      return null;
    }

    String[] parts = encoded.split("\\.");
    if (parts.length < 2 || parts.length > 3) {
      throw new IllegalArgumentException("Parsing error");
    }

    byte[] bytes = Base64Url.decode(parts[1]);
    return JsonSerialization.readValue(bytes, clazz);
  }

  /**
   * Verifies the token and returns its claims, or throws. This is the security boundary: it checks the
   * signature against the realm's published keys, the issuer against the configured realm, and that the
   * token is currently active. The earlier implementation decoded the payload and checked only expiry,
   * so a token with any signature — or none — was accepted as whoever its payload named.
   *
   * An expired (or not-yet-valid) token is reported as {@link AccessTokenExpiredException} so the client
   * is told to refresh rather than to log out; every other failure — a bad or absent signature, a wrong
   * issuer, a malformed token — is an invalid credential and is reported as
   * {@link InvalidOfflineAccessTokenException}. Both map to 401.
   */
  private static AccessToken verifyToken(String token, KeycloakDeployment deployment) throws CedarAccessException {
    if (token == null) {
      throw new AccessTokenMissingException();
    }
    try {
      return AdapterTokenVerifier.verifyToken(token, deployment);
    } catch (TokenNotActiveException e) {
      JsonWebToken jwt = e.getToken();
      int expiration = (jwt != null && jwt.getExp() != null) ? jwt.getExp().intValue() : 0;
      throw new AccessTokenExpiredException(expiration);
    } catch (VerificationException e) {
      throw new InvalidOfflineAccessTokenException(e);
    }
  }

  public static CedarUser getUserFromAuthRequest(LinkedDataUtil linkedDataUtil, AuthRequest authRequest,
                                                 IUserService userService, KeycloakDeployment deployment)
      throws CedarAccessException {
    String token = authRequest.getAuthString();
    AccessToken accessToken = verifyToken(token, deployment);
    String userUuid = accessToken.getSubject();
    String userId = linkedDataUtil.getUserId(userUuid);
    CedarUserId uid = CedarUserId.build(userId);
    CedarUser user = null;
    try {
      user = userService.findUser(uid);
    } catch (IOException e) {
      log.error("Error while getting user", e);
    }
    return user;
  }

  public static KeycloakUtilInfo initKeycloak(CedarConfig cedarConfig) {
    KeycloakUtilInfo kcInfo = new KeycloakUtilInfo();

    kcInfo.setCedarAdminUserName(cedarConfig.getAdminUserConfig().getUserName());
    kcInfo.setCedarAdminUserPassword(cedarConfig.getAdminUserConfig().getPassword());
    kcInfo.setCedarAdminUserApiKey(cedarConfig.getAdminUserConfig().getApiKey());
    kcInfo.setKeycloakClientId(cedarConfig.getKeycloakConfig().getClientId());
    kcInfo.setAllowInsecureTls(cedarConfig.getKeycloakConfig().isAllowInsecureTls());

    KeycloakDeploymentProvider keycloakDeploymentProvider = new KeycloakDeploymentProvider();
    KeycloakDeployment keycloakDeployment = keycloakDeploymentProvider.buildDeployment(cedarConfig.getKeycloakConfig());

    kcInfo.setKeycloakRealmName(keycloakDeployment.getRealm());
    kcInfo.setKeycloakBaseURI(keycloakDeployment.getAuthServerBaseUrl());

    return kcInfo;
  }

  private static JacksonJsonProvider getCustomizedJacksonJsonProvider() {
    ObjectMapper m = new ObjectMapper();
    JacksonJsonProvider jacksonJsonProvider = new JacksonXmlBindJsonProvider();
    jacksonJsonProvider.setMapper(m);

    m.addHandler(new DeserializationProblemHandler() {
      @Override
      public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser jp, JsonDeserializer<?>
          deserializer, Object beanOrClass, String propertyName) throws IOException {
        //out.info("Run into unknown property:" + propertyName + "=>" + ctxt.getParser().getText());
        if ("access_token".equals(propertyName)) {
          if (beanOrClass instanceof AccessTokenResponse atr) {
            String text = ctxt.getParser().getText();
            atr.setToken(text);
          }
        } else {
          super.handleUnknownProperty(ctxt, jp, deserializer, beanOrClass, propertyName);
        }
        return true;
      }
    });
    return jacksonJsonProvider;
  }

  static SSLContext buildInsecureSslContext(boolean allowInsecureTls) {
    if (!allowInsecureTls) {
      return null;
    }
    try {
      TrustManager[] trustAllCerts = new TrustManager[] {
          new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
              return new X509Certificate[0];
            }

            public void checkClientTrusted(X509Certificate[] certs, String authType) {
            }

            public void checkServerTrusted(X509Certificate[] certs, String authType) {
            }
          }
      };

      SSLContext sslContext = SSLContext.getInstance("TLS");
      sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
      return sslContext;
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Unable to initialize the development-only Keycloak TLS bypass", e);
    }
  }

  public static Keycloak buildKeycloak(KeycloakUtilInfo kcInfo) {
    SSLContext insecureSslContext = buildInsecureSslContext(kcInfo.isAllowInsecureTls());

    JacksonJsonProvider jacksonJsonProvider = getCustomizedJacksonJsonProvider();

    // TODO: add connectionPoolSize(10)
    // Instantiate the RESTEasy builder concretely rather than via ResteasyClientBuilder.newBuilder():
    // under jakarta ws.rs 3.0 the ClientBuilder ServiceLoader also finds Jersey on the classpath and
    // may resolve to it, and the Keycloak admin client requires a genuine RESTEasy client (it casts
    // the WebTarget to ResteasyWebTarget).
    ResteasyClientBuilderImpl resteasyClientBuilder = new ResteasyClientBuilderImpl();
    resteasyClientBuilder.register(jacksonJsonProvider);
    if (insecureSslContext != null) {
      log.warn("Keycloak admin-client certificate verification is disabled by explicit development opt-in");
      resteasyClientBuilder.sslContext(insecureSslContext);
    }
    Client resteasyClient = resteasyClientBuilder.build();

    return KeycloakBuilder.builder()
        .serverUrl(kcInfo.getKeycloakBaseURI())
        .realm(kcInfo.getKeycloakRealmName())
        .username(kcInfo.getCedarAdminUserName())
        .password(kcInfo.getCedarAdminUserPassword())
        .clientId(kcInfo.getKeycloakClientId())
        .resteasyClient(resteasyClient)
        .build();
  }
}
