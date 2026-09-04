package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKeyMap;

import java.util.List;

/**
 * The guard that keeps a credential write from being derived from a record that could not be read.
 * Writing in that state replaces both stored properties with a key set built from nothing.
 */
public class Neo4JProxyUserApiKeyGuardTest {

  private static FolderServerUser userWith(CedarUserApiKeyMap map) {
    FolderServerUser user = new FolderServerUser();
    user.setId("https://metadatacenter.orgx/users/test");
    user.setApiKeys(List.of("secretA"));
    user.setApiKeyMap(map);
    return user;
  }

  @Test
  public void aWriteIsRefusedWhenTheStoredMapCouldNotBeRead() {
    Assertions.assertTrue(
        Neo4JProxyUser.hasUnreadableApiKeyMap(userWith(new CedarUserApiKeyMap("{\"secretA\":{\"id\":"))));
  }

  @Test
  public void aReadableMapIsWritable() {
    Assertions.assertFalse(Neo4JProxyUser.hasUnreadableApiKeyMap(
        userWith(new CedarUserApiKeyMap("{\"secretA\":{\"id\":\"idA\",\"key\":\"secretA\"}}"))));
  }

  @Test
  public void aUserWhoGenuinelyHasNoKeysIsWritable() {
    Assertions.assertFalse(Neo4JProxyUser.hasUnreadableApiKeyMap(userWith(new CedarUserApiKeyMap("{}"))));
  }
}
