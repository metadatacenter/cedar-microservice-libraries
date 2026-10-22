package org.metadatacenter.server.security.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;

import java.util.List;

class CedarUserUtilTest {

  @Test
  void generatedApiKeysAreRandom256BitValues() {
    String first = CedarUserUtil.generateApiKey();
    String second = CedarUserUtil.generateApiKey();

    Assertions.assertTrue(first.matches("[0-9a-f]{64}"), first);
    Assertions.assertTrue(second.matches("[0-9a-f]{64}"), second);
    Assertions.assertNotEquals(first, second);
  }

  /**
   * A super role the blueprint grants nothing still yields an account, one holding no role. The
   * admin tool's permission update reads the same roles and adds them to an account.
   */
  @Test
  void aSuperRoleTheBlueprintGrantsNoRoleYieldsAnAccountWithoutRoles() throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    BlueprintUserProfile blueprint = mapper.readValue("""
        {"defaultAPIKey": {"serviceName": "CEDAR", "description": "Auto-generated apiKey for CEDAR"},
         "defaultRoles": {"normal": ["defaultUser"]},
         "uiPreferences": {"folderView": {"sortBy": "name", "sortDirection": "asc", "viewMode": "grid"},
                           "stylesheet": "default"}}
        """, BlueprintUserProfile.class);
    CedarConfig config = mapper.readValue(
        "{\"adminUser\": {\"userName\": \"admin\"}, \"caDSRAdminUser\": {\"userName\": \"cadsr\"}}", CedarConfig.class);

    CedarUser user = CedarUserUtil.createUserFromBlueprint(blueprint,
        new CedarUserExtract("https://metadatacenter.org/users/someone", "First", "Last", "someone@test.com"),
        CedarSuperRole.BUILT_IN_ADMIN, config, "someone");

    Assertions.assertEquals(List.of(), CedarUserUtil.getRolesForType(blueprint, CedarSuperRole.BUILT_IN_ADMIN));
    Assertions.assertEquals(List.of(), user.getRoles());
    Assertions.assertEquals(List.of(), user.getPermissions());
  }
}
