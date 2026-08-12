package org.metadatacenter.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.IUserService;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;

import java.time.LocalDateTime;
import java.util.List;

public interface UserService extends IUserService {

  CedarUser createUser(CedarUser user);

  CedarUser findUser(CedarUserId userId);

  CedarUser findUserByApiKey(String apiKey);

  BackendCallResult<CedarUser> updateUser(CedarUser user);

  BackendCallResult<CedarUser> patchUser(CedarUserId userId, JsonNode modifications);

  /**
   * Changes to a user's API keys go through these rather than through {@link #updateUser}, which
   * writes the whole user from a caller-held copy. Each reads the current keys and writes the change
   * as one atomic step, and touches no field of the user beyond the keys.
   */
  BackendCallResult<CedarUser> addApiKey(CedarUserId userId, CedarUserApiKey newApiKey, int maxApiKeys);

  BackendCallResult<CedarUser> regenerateApiKey(CedarUserId userId, String keyValue, String newKeyValue,
                                                LocalDateTime newCreationDate);

  BackendCallResult<CedarUser> deleteApiKey(CedarUserId userId, String keyValue);

  List<CedarUser> findAll();

}
