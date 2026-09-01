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
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
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
    params.put(NodeProperty.NAME_LOWER, displayName.toLowerCase());
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

  public static CypherParameters touchUser(CedarUserId userId) {
    Instant now = Instant.now();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, userId.getId());
    params.put(NodeProperty.LAST_UPDATED_ON, CedarConstants.xsdDateTimeFormatter.format(now));
    params.put(NodeProperty.LAST_UPDATED_ON_TS, now.getEpochSecond());
    return params;
  }

  public static CypherParameters updateUserProfile(CedarUserId userId, CedarUserUIPreferences uiPreferences)
      throws CedarProcessingException {
    CypherParameters params = touchUser(userId);
    try {
      params.put(NodeProperty.UI_PREFERENCES, JsonMapper.MAPPER.writeValueAsString(uiPreferences));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
    return params;
  }

  public static CypherParameters setUserHomeFolderId(CedarUserId userId, String homeFolderId) {
    CypherParameters params = touchUser(userId);
    params.put(NodeProperty.HOME_FOLDER_ID, homeFolderId);
    return params;
  }

  public static CypherParameters replaceUserRolesAndPermissions(CedarUserId userId, List<CedarUserRole> roles,
                                                                 List<String> permissions) {
    CypherParameters params = touchUser(userId);
    List<String> roleValues = new ArrayList<>();
    for (CedarUserRole role : roles) {
      if (role != null) {
        roleValues.add(role.getValue());
      }
    }
    params.put(NodeProperty.ROLES, roleValues);
    params.put(NodeProperty.PERMISSIONS, permissions);
    return params;
  }

  /**
   * The API key properties as the graph holds them: the key values as a list, and the keys
   * themselves as a JSON object keyed by value. Both are derived from the one list, so they cannot
   * fall out of step with each other.
   */
  public static CypherParameters updateUserApiKeys(CedarUserId userId, List<CedarUserApiKey> apiKeys)
      throws CedarProcessingException {
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, userId.getId());

    List<String> justKeys = new ArrayList<>();
    Map<String, CedarUserApiKey> keyMap = new HashMap<>();
    for (CedarUserApiKey key : apiKeys) {
      justKeys.add(key.getKey());
      keyMap.put(key.getKey(), key);
    }
    params.put(NodeProperty.API_KEYS, justKeys);
    try {
      params.put(NodeProperty.API_KEY_MAP, JsonMapper.MAPPER.writeValueAsString(keyMap));
    } catch (JsonProcessingException e) {
      throw new CedarProcessingException(e);
    }
    return params;
  }

  public static CypherParameters getUserByApiKey(String apiKey) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.API_KEY, apiKey);
    return params;
  }

}
