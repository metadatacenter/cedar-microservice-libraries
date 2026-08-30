package org.metadatacenter.util.test;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarApiKeyAuthRequest;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.model.user.CedarUserUIFolderView;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.security.model.user.SortDirection;
import org.metadatacenter.server.security.model.user.ViewMode;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Provides authenticated identities for integration tests without any live auth backend. The
 * test users are built in memory with the roles the artifact-handling endpoints require,
 * registered with the Authorization holder through InMemoryUserService, and their API keys are
 * used in the Authorization header of test requests. TestUserUtil offers the same headers backed
 * by a seeded graph; this class is the backend-free replacement.
 *
 * Call installInMemoryUserService once per test class, after the DropwizardAppRule has started:
 * the application's own startup wires the Neo4j-backed user service, and this call replaces it
 * for the lifetime of the test JVM.
 */
public final class TestAuthUtil {

  private static final String TEST_USER_1_API_KEY = "11111111-2222-3333-4444-555555555555";
  private static final String TEST_USER_2_API_KEY = "66666666-7777-8888-9999-aaaaaaaaaaaa";

  // The admin user has no id in AdminUserConfig; a fixed synthetic id suffices, since API-key
  // authentication only needs the key to resolve to a user
  private static final String ADMIN_USER_ID = "https://metadatacenter.org/users/00000000-aaaa-bbbb-cccc-000000000000";

  private static CedarUser testUser1;
  private static CedarUser testUser2;
  private static CedarUser adminUser;
  private static InMemoryUserService inMemoryUserService;

  private TestAuthUtil() {
  }

  public static synchronized CedarUser getTestUser1(CedarConfig cedarConfig) {
    if (testUser1 == null) {
      testUser1 = buildTestUser(cedarConfig.getTestUsers().getTestUser1().getId(), "Test1", TEST_USER_1_API_KEY);
    }
    return testUser1;
  }

  public static synchronized CedarUser getTestUser2(CedarConfig cedarConfig) {
    if (testUser2 == null) {
      testUser2 = buildTestUser(cedarConfig.getTestUsers().getTestUser2().getId(), "Test2", TEST_USER_2_API_KEY);
    }
    return testUser2;
  }

  public static synchronized CedarUser getAdminUser(CedarConfig cedarConfig) {
    if (adminUser == null) {
      adminUser = buildTestUser(ADMIN_USER_ID, "Admin", cedarConfig.getAdminUserConfig().getApiKey());
      adminUser.getRoles().clear();
      for (CedarUserRole role : CedarUserRole.values()) {
        adminUser.getRoles().add(role);
      }
      CedarUserRolePermissionUtil.expandRolesIntoPermissions(adminUser);
    }
    return adminUser;
  }

  /**
   * The in-memory user service holding the test users. Exposed so tests can also inject it where
   * a server uses the user service beyond authentication (for example
   * UsersResource.injectUserService in the user server).
   */
  public static synchronized InMemoryUserService getInMemoryUserService(CedarConfig cedarConfig) {
    if (inMemoryUserService == null) {
      inMemoryUserService = new InMemoryUserService(getTestUser1(cedarConfig), getTestUser2(cedarConfig), getAdminUser(cedarConfig));
    }
    return inMemoryUserService;
  }

  public static void installInMemoryUserService(CedarConfig cedarConfig) {
    Authorization.setUserService(getInMemoryUserService(cedarConfig));
  }

  public static String getTestUser1AuthHeader(CedarConfig cedarConfig) {
    return authHeaderFor(getTestUser1(cedarConfig));
  }

  public static String getTestUser2AuthHeader(CedarConfig cedarConfig) {
    return authHeaderFor(getTestUser2(cedarConfig));
  }

  public static String getAdminUserAuthHeader(CedarConfig cedarConfig) {
    return authHeaderFor(getAdminUser(cedarConfig));
  }

  private static String authHeaderFor(CedarUser user) {
    return new CedarApiKeyAuthRequest(user.getFirstActiveApiKey()).getAuthHeader();
  }

  private static CedarUser buildTestUser(String id, String firstName, String apiKey) {
    CedarUser user = new CedarUser();
    user.setId(id);
    user.setFirstName(firstName);
    user.setLastName("User");
    user.setEmail(firstName.toLowerCase() + "@test.com");

    CedarUserApiKey apiKeyObject = new CedarUserApiKey();
    apiKeyObject.setId(UUID.randomUUID().toString());
    apiKeyObject.setKey(apiKey);
    apiKeyObject.setServiceName("CEDAR");
    apiKeyObject.setDescription("apiKey for the integration test user");
    apiKeyObject.setCreationDate(LocalDateTime.now());
    apiKeyObject.setEnabled(true);
    user.getApiKeys().add(apiKeyObject);

    user.getRoles().add(CedarUserRole.DEFAULT_USER);
    user.getRoles().add(CedarUserRole.TEMPLATE_CREATOR);
    user.getRoles().add(CedarUserRole.METADATA_CREATOR);
    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    // Provisioned users carry populated UI preferences (CedarUserUtil fills them from the
    // blueprint); the profile-patching machinery relies on the fields being present
    CedarUserUIPreferences uiPreferences = user.getUiPreferences();
    uiPreferences.setStylesheet("default");
    CedarUserUIFolderView folderView = uiPreferences.getFolderView();
    folderView.setSortBy("name");
    folderView.setSortDirection(SortDirection.forValue("asc"));
    folderView.setViewMode(ViewMode.forValue("grid"));
    return user;
  }

}
