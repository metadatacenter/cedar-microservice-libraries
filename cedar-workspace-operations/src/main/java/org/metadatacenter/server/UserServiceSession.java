package org.metadatacenter.server;

import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;

import java.util.List;

public interface UserServiceSession {

  List<FolderServerUser> findUsers();

  FolderServerUser getUser(CedarUserId userId);

  boolean addUserToEverybodyGroup(CedarUserId userId) throws CedarProcessingException;

  long getUserCount();
}
