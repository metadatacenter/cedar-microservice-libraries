package org.metadatacenter.util.test;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarApiKeyAuthRequest;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.model.user.CedarUserUIFolderView;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.security.model.user.SortDirection;
import org.metadatacenter.server.security.model.user.ViewMode;
import org.metadatacenter.server.security.util.CedarUserUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Provides authenticated identities for integration tests without any live auth backend. The test
 * users are built in memory, registered with the Authorization holder through InMemoryUserService,
 * and their API keys are used in the Authorization header of test requests. TestUserUtil offers the
 * same headers backed by a seeded graph; this class is the backend-free replacement.
 *
 * <p>Their roles come from the same place a deployment's do: the blueprint user profile in
 * cedar-main.yml, read through {@link CedarUserUtil#getRolesForType}, which is what the resource
 * server calls when Keycloak reports a user it has not seen before. Test users 1 and 2 are built as
 * {@link CedarSuperRole#NORMAL} and the admin as {@link CedarSuperRole#BUILT_IN_ADMIN}, so an
 * ordinary actor in a test holds exactly the permissions an ordinary account holds in production.
 *
 * <p>This was once a hand-written list of three roles, and the gap it opened is worth stating. The
 * blueprint grants a normal user groupAdministrator and categoryAdministrator on top of those three;
 * the fixture did not. Every group endpoint gates on a GROUP_* permission that the
 * groupAdministrator role carries, so an ordinary test actor was refused where an ordinary real user
 * is served, and two authorization matrices pinned that refusal as the intended design. The
 * authorization a suite asserts is only as truthful as the actor it asserts it about, so the roles
 * are derived rather than restated.
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
      testUser1 = buildTestUser(cedarConfig, cedarConfig.getTestUsers().getTestUser1().getId(), "Test1",
          TEST_USER_1_API_KEY, CedarSuperRole.NORMAL);
    }
    return testUser1;
  }

  public static synchronized CedarUser getTestUser2(CedarConfig cedarConfig) {
    if (testUser2 == null) {
      testUser2 = buildTestUser(cedarConfig, cedarConfig.getTestUsers().getTestUser2().getId(), "Test2",
          TEST_USER_2_API_KEY, CedarSuperRole.NORMAL);
    }
    return testUser2;
  }

  /**
   * The built-in administrator, built from the blueprint like the ordinary users. The blueprint
   * grants this super role every role the estate defines, so the identity is what it always was;
   * deriving it means a role added to the enum but withheld from the blueprint is withheld here too,
   * as it would be from the real administrator.
   */
  public static synchronized CedarUser getAdminUser(CedarConfig cedarConfig) {
    if (adminUser == null) {
      adminUser = buildTestUser(cedarConfig, ADMIN_USER_ID, "Admin",
          cedarConfig.getAdminUserConfig().getApiKey(), CedarSuperRole.BUILT_IN_ADMIN);
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

  /**
   * The roles a deployment gives this kind of user, read from the blueprint the servers load. A
   * blueprint that names none for the super role is a configuration this fixture cannot stand in for:
   * the resulting user would hold no permission at all and every authorization assertion made about
   * it would pass for the wrong reason, so it fails here instead.
   */
  private static List<CedarUserRole> rolesFromBlueprint(CedarConfig cedarConfig, CedarSuperRole superRole) {
    List<CedarUserRole> roles = CedarUserUtil.getRolesForType(cedarConfig.getBlueprintUserProfile(), superRole);
    if (roles == null || roles.isEmpty()) {
      throw new IllegalStateException(
          "The blueprint user profile grants no role to the " + superRole.getValue() + " super role, "
              + "so a test user of that kind cannot be built");
    }
    return roles;
  }

  private static CedarUser buildTestUser(CedarConfig cedarConfig, String id, String firstName, String apiKey,
                                         CedarSuperRole superRole) {
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

    user.getRoles().addAll(rolesFromBlueprint(cedarConfig, superRole));
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
