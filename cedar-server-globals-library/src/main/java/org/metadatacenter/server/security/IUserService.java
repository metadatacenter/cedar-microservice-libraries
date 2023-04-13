package org.metadatacenter.server.security;

import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.io.IOException;

public interface IUserService {
  CedarUser findUserByApiKey(String apiKey) throws IOException;

  CedarUser findUser(CedarUserId id) throws IOException;

}
