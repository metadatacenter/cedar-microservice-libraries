package org.metadatacenter.server.security;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import jakarta.ws.rs.client.Client;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.common.util.Base64Url;
import org.keycloak.representations.AccessToken;
import org.keycloak.representations.AccessTokenResponse;
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

import java.io.IOException;

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

  private static AccessToken checkIfTokenIsStillActiveByUserInfo(String token) throws CedarAccessException {
    AccessToken accessToken;
    if (token == null) {
      throw new AccessTokenMissingException();
    }
    try {
      accessToken = KeycloakUtils.parseToken(token, AccessToken.class);
    } catch (IOException e) {
      throw new InvalidOfflineAccessTokenException();
    }
    if (accessToken == null) {
      throw new InvalidOfflineAccessTokenException();
    } else if (accessToken.isExpired()) {
      throw new AccessTokenExpiredException(accessToken.getExpiration());
    } else {
      return accessToken;
    }
  }

  public static CedarUser getUserFromAuthRequest(LinkedDataUtil linkedDataUtil, AuthRequest authRequest,
                                                 IUserService userService) throws CedarAccessException {
    String token = authRequest.getAuthString();
    AccessToken accessToken = checkIfTokenIsStillActiveByUserInfo(token);
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

    KeycloakDeploymentProvider keycloakDeploymentProvider = new KeycloakDeploymentProvider();
    KeycloakDeployment keycloakDeployment = keycloakDeploymentProvider.buildDeployment(cedarConfig.getKeycloakConfig());

    kcInfo.setKeycloakRealmName(keycloakDeployment.getRealm());
    kcInfo.setKeycloakBaseURI(keycloakDeployment.getAuthServerBaseUrl());

    return kcInfo;
  }

  private static JacksonJsonProvider getCustomizedJacksonJsonProvider() {
    ObjectMapper m = new ObjectMapper();
    JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider();
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

  public static Keycloak buildKeycloak(KeycloakUtilInfo kcInfo) {
    JacksonJsonProvider jacksonJsonProvider = getCustomizedJacksonJsonProvider();

    // TODO: add connectionPoolSize(10)
    Client resteasyClient = ResteasyClientBuilder.newBuilder().register(jacksonJsonProvider).build();

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
