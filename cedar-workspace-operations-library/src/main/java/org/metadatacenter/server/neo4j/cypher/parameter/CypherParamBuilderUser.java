package org.metadatacenter.server.neo4j.cypher.parameter;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.util.CedarUserNameUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CypherParamBuilderUser extends AbstractCypherParamBuilder {

  protected static final Logger log = LoggerFactory.getLogger(CypherParamBuilderUser.class);

  public static CypherParameters createUser(CedarUser user, CedarConfig cedarConfig) throws CedarProcessingException {
    String displayName = CedarUserNameUtil.getDisplayName(cedarConfig, user);
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, user.getId());
    params.put(NodeProperty.NAME, displayName);
    params.put(NodeProperty.FIRST_NAME, user.getFirstName());
    params.put(NodeProperty.LAST_NAME, user.getLastName());
    params.put(NodeProperty.EMAIL, user.getEmail());
    params.put(NodeProperty.CREATED_ON, nowString);
    params.put(NodeProperty.CREATED_ON_TS, nowTS);
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);
    params.put(NodeProperty.RESOURCE_TYPE, CedarResourceType.USER.getValue());

    List<String> justKeys = new ArrayList<>();
    for (CedarUserApiKey key : user.getApiKeys()) {
      justKeys.add(key.getKey());
    }
    params.put(NodeProperty.API_KEYS, justKeys);

    Map<String, CedarUserApiKey> keyMap = new HashMap<>();
    for (CedarUserApiKey key : user.getApiKeys()) {
      keyMap.put(key.getKey(), key);
    }
    try {
      params.put(NodeProperty.API_KEY_MAP, JsonMapper.MAPPER.writeValueAsString(keyMap));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }

    List<String> justRoles = new ArrayList<>();
    for (CedarUserRole role : user.getRoles()) {
      justRoles.add(role.getValue());
    }
    params.put(NodeProperty.ROLES, justRoles);

    params.put(NodeProperty.PERMISSIONS, user.getPermissions());

    try {
      params.put(NodeProperty.UI_PREFERENCES, JsonMapper.MAPPER.writeValueAsString(user.getUiPreferences()));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }

    return params;
  }

  public static CypherParameters matchUserId(CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters getUserById(CedarUserId userId) {
    return matchResourceByIdentity(userId);
  }

  public static CypherParameters getUserByApiKey(String apiKey) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.API_KEY, apiKey);
    return params;
  }

  public static CypherParameters updateUser(CedarUser user, CedarConfig cedarConfig) throws CedarProcessingException {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, user.getId());
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);

    String displayName = CedarUserNameUtil.getDisplayName(cedarConfig, user);

    params.put(NodeProperty.NAME, displayName);
    params.put(NodeProperty.FIRST_NAME, user.getFirstName());
    params.put(NodeProperty.LAST_NAME, user.getLastName());
    params.put(NodeProperty.EMAIL, user.getEmail());
    params.put(NodeProperty.HOME_FOLDER_ID, user.getHomeFolderId());

    List<String> justKeys = new ArrayList<>();
    for (CedarUserApiKey key : user.getApiKeys()) {
      justKeys.add(key.getKey());
    }
    params.put(NodeProperty.API_KEYS, justKeys);

    Map<String, CedarUserApiKey> keyMap = new HashMap<>();
    for (CedarUserApiKey key : user.getApiKeys()) {
      keyMap.put(key.getKey(), key);
    }
    try {
      params.put(NodeProperty.API_KEY_MAP, JsonMapper.MAPPER.writeValueAsString(keyMap));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }

    List<String> justRoles = new ArrayList<>();
    for (CedarUserRole role : user.getRoles()) {
      if (role != null) {
        justRoles.add(role.getValue());
      }
    }
    params.put(NodeProperty.ROLES, justRoles);

    params.put(NodeProperty.PERMISSIONS, user.getPermissions());

    try {
      params.put(NodeProperty.UI_PREFERENCES, JsonMapper.MAPPER.writeValueAsString(user.getUiPreferences()));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }

    return params;

  }
}
