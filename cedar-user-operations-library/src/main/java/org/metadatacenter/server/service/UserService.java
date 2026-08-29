package org.metadatacenter.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.IUserService;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;

import java.time.LocalDateTime;
import java.util.List;

public interface UserService extends IUserService {

  CedarUser createUser(CedarUser user);

  CedarUser findUser(CedarUserId userId);

  CedarUser findUserByApiKey(String apiKey);

  BackendCallResult<CedarUser> setHomeFolderId(CedarUserId userId, String homeFolderId);

  BackendCallResult<CedarUser> replaceRolesAndPermissions(CedarUserId userId, List<CedarUserRole> roles,
                                                          List<String> permissions);

  BackendCallResult<CedarUser> replaceUiPreferences(CedarUserId userId, CedarUserUIPreferences uiPreferences);

  BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications);

  /**
   * Each API-key change reads the current keys and writes the change as one atomic step, and touches
   * no field of the user beyond the keys.
   */
  BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys);

  BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyId, String newKeyValue,
                                                LocalDateTime newCreationDate);

  BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyId);

  List<CedarUser> findAll();

}
