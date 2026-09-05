package org.metadatacenter.model.folderserver.basic;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserApiKeyMap;

import java.util.List;

public class FolderServerUserApiKeyTest {

  private static FolderServerUser userWith(List<String> storedSecrets, CedarUserApiKeyMap map) {
    FolderServerUser user = new FolderServerUser();
    user.setId("https://metadatacenter.orgx/users/test");
    user.setApiKeys(storedSecrets);
    user.setApiKeyMap(map);
    return user;
  }

  @Test
  public void keysComeFromTheMapWhenItCanBeRead() {
    CedarUserApiKeyMap map = new CedarUserApiKeyMap(
        "{\"secretA\":{\"id\":\"idA\",\"key\":\"secretA\",\"serviceName\":\"nightly\",\"enabled\":true}}");

    List<CedarUserApiKey> keys = userWith(List.of("secretA"), map).buildUser().getApiKeys();

    Assertions.assertEquals(1, keys.size());
    Assertions.assertEquals("idA", keys.get(0).getId());
    Assertions.assertEquals("nightly", keys.get(0).getServiceName(), "the map carries the metadata");
  }

  /**
   * Authentication matches the apiKeys property, so a profile that reports none while those secrets
   * still work is telling the user something the graph disagrees with.
   */
  @Test
  public void anUnreadableMapDoesNotReportTheUserAsHavingNoKeys() {
    List<CedarUserApiKey> keys =
        userWith(List.of("secretA", "secretB"), new CedarUserApiKeyMap("{\"secretA\":{\"id\":"))
            .buildUser().getApiKeys();

    Assertions.assertEquals(2, keys.size(), "the secrets that authenticate must still be reported");
    Assertions.assertEquals(List.of("secretA", "secretB"), keys.stream().map(CedarUserApiKey::getKey).toList());
    Assertions.assertTrue(keys.get(0).isEnabled(), "the graph accepts this secret, so it is not disabled");
  }

  @Test
  public void aUserWithNoKeysStillReportsNone() {
    List<CedarUserApiKey> keys = userWith(List.of(), new CedarUserApiKeyMap("{}")).buildUser().getApiKeys();

    Assertions.assertTrue(keys.isEmpty());
  }
}
