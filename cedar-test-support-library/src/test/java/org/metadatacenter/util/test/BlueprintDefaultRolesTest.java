package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.util.CedarUserUtil;

import java.util.List;
import java.util.Map;

/**
 * What a CEDAR account is allowed to do the moment it is created.
 *
 * <p>An account's authority begins with the blueprint user profile in cedar-main.yml: the resource
 * server builds a user Keycloak has not reported before from it, and the roles it names there expand
 * into the permission set every authorization gate in the estate consults. Nothing asserted what
 * those roles are. The consequence was not theoretical — the test fixture listed three roles by hand
 * where the blueprint grants five, so suites reasoned about an ordinary user who could not exist,
 * and two group-server authorization matrices pinned a refusal that no real deployment issues.
 *
 * <p>So this test states the grant literally rather than deriving it, which is the only form that
 * can fail when the grant changes. It belongs to no one service: the blueprint is configuration, the
 * expansion is in the globals library and the accessor is in the auth library, and this module is
 * where all three are on the classpath together.
 *
 * <p>The permissions asserted below are the decisive ones rather than the whole set. A test that
 * recomputed the full expansion from the same table the production code reads would agree with it
 * however wrong both were.
 */
public class BlueprintDefaultRolesTest {

  /**
   * The roles a normal account receives. groupAdministrator is the one to look at twice: its name
   * describes administering groups, and every account holds it, which is what makes the group
   * server's four GROUP_* gates unable to narrow anything.
   */
  private static final List<CedarUserRole> NORMAL_ROLES = List.of(
      CedarUserRole.DEFAULT_USER,
      CedarUserRole.TEMPLATE_CREATOR,
      CedarUserRole.METADATA_CREATOR,
      CedarUserRole.GROUP_ADMINISTRATOR,
      CedarUserRole.CATEGORY_ADMINISTRATOR);

  private Map<String, String> previousOverride;

  @BeforeEach
  public void setEnvironment() {
    previousOverride = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;
    CedarEnvironmentSource.setOverride(CedarTestEnvironment.build());
  }

  @AfterEach
  public void restoreEnvironment() {
    CedarEnvironmentSource.setOverride(previousOverride);
  }

  private static CedarConfig config() throws Exception {
    return CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_GROUP));
  }

  @Test
  public void aNormalAccountReceivesExactlyTheseRoles() throws Exception {
    List<CedarUserRole> roles =
        CedarUserUtil.getRolesForType(config().getBlueprintUserProfile(), CedarSuperRole.NORMAL);

    Assertions.assertEquals(NORMAL_ROLES, roles,
        "the roles a normal account receives changed; every authorization matrix in the estate "
            + "describes an actor holding them");
  }

  @Test
  public void aBuiltInAdministratorReceivesEveryRole() throws Exception {
    List<CedarUserRole> roles =
        CedarUserUtil.getRolesForType(config().getBlueprintUserProfile(), CedarSuperRole.BUILT_IN_ADMIN);

    Assertions.assertEquals(List.of(CedarUserRole.values()), roles,
        "the built-in administrator is the actor suites use to prove an endpoint works at all, so a "
            + "role it does not hold is a capability nothing exercises");
  }

  /**
   * The permissions that decide the group server's answers. Every account holds the four GROUP_*
   * permissions, so those gates admit everyone; the override that skips the per-group administrator
   * check is the one an account must not hold, and holding it once made every group writable by
   * anyone.
   */
  @Test
  public void aNormalAccountHoldsTheGroupReadPermissionAndNotThePrivilegedOverride() throws Exception {
    CedarUser normal = normalAccount();

    Assertions.assertTrue(normal.has(CedarPermission.GROUP_READ),
        "an ordinary account holds GROUP_READ, so no group endpoint gated on it alone is restricted");
    Assertions.assertTrue(normal.has(CedarPermission.GROUP_CREATE));
    Assertions.assertTrue(normal.has(CedarPermission.GROUP_UPDATE));
    Assertions.assertTrue(normal.has(CedarPermission.GROUP_DELETE));

    Assertions.assertFalse(normal.has(CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP),
        "the override that bypasses the per-group administrator check must stay with the privileged "
            + "role the built-in admin alone holds");
  }

  @Test
  public void aNormalAccountAdministersCategoriesButCannotOverrideTheirPermissions() throws Exception {
    CedarUser normal = normalAccount();

    Assertions.assertTrue(normal.has(CedarPermission.CATEGORY_CREATE));
    Assertions.assertTrue(normal.has(CedarPermission.CATEGORY_READ));
    Assertions.assertTrue(normal.has(CedarPermission.CATEGORY_UPDATE));
    Assertions.assertTrue(normal.has(CedarPermission.CATEGORY_DELETE));

    Assertions.assertFalse(normal.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY));
    Assertions.assertFalse(normal.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY));
  }

  /**
   * A normal account holds no administrative permission over users, the filesystem, artifacts,
   * search or the monitor. These are the refusals the authorization matrices rely on, so they are
   * asserted about the account itself rather than only about the endpoints that check them.
   */
  @Test
  public void aNormalAccountHoldsNoEstateAdministrationPermission() throws Exception {
    CedarUser normal = normalAccount();

    Assertions.assertFalse(normal.has(CedarPermission.USER_READ));
    Assertions.assertFalse(normal.has(CedarPermission.USER_UPDATE));
    Assertions.assertFalse(normal.has(CedarPermission.READ_NOT_READABLE_NODE));
    Assertions.assertFalse(normal.has(CedarPermission.WRITE_NOT_WRITABLE_NODE));
    Assertions.assertFalse(normal.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE));
    Assertions.assertFalse(normal.has(CedarPermission.WRITE_ARTIFACT_VERBATIM));
    Assertions.assertFalse(normal.has(CedarPermission.SEARCH_INDEX_REINDEX));
    Assertions.assertFalse(normal.has(CedarPermission.MONITOR_READ));
    Assertions.assertFalse(normal.has(CedarPermission.SEND_PROCESS_MESSAGE));
  }

  /**
   * The fixture and the blueprint have to stay the same thing. TestAuthUtil derives the roles now,
   * so this asserts a property of that derivation rather than a second copy of the list: whatever a
   * deployment grants a normal account is what a test actor holds.
   */
  @Test
  public void theFixtureUsersHoldTheRolesTheBlueprintGrants() throws Exception {
    CedarConfig config = config();

    Assertions.assertEquals(NORMAL_ROLES, TestAuthUtil.getTestUser1(config).getRoles(),
        "test user 1 no longer holds what a normal account holds");
    Assertions.assertEquals(NORMAL_ROLES, TestAuthUtil.getTestUser2(config).getRoles(),
        "test user 2 no longer holds what a normal account holds");
    Assertions.assertEquals(List.of(CedarUserRole.values()), TestAuthUtil.getAdminUser(config).getRoles(),
        "the fixture administrator no longer holds what the built-in administrator holds");
  }

  private CedarUser normalAccount() throws Exception {
    return TestAuthUtil.getTestUser1(config());
  }

}
