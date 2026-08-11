package org.metadatacenter.server.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserRole;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/** Exhaustive unit matrix for the role-to-authority expansion boundary. */
class CedarUserRolePermissionUtilTest {

  private static final Map<CedarUserRole, Set<CedarPermission>> EXPECTED = expectedRoleMap();

  @ParameterizedTest(name = "{0}")
  @MethodSource("rolesAndExpectedPermissions")
  void eachRoleExpandsToExactlyItsDeclaredPermissions(CedarUserRole role,
                                                       Set<CedarPermission> expected) {
    CedarUser user = userWithRoles(role);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertEquals(permissionNames(expected), user.getPermissions());
  }

  @ParameterizedTest
  @EnumSource(CedarPermission.class)
  void everyPermissionIsGrantedByExactlyTheExpectedRoles(CedarPermission permission) {
    for (CedarUserRole role : CedarUserRole.values()) {
      CedarUser user = userWithRoles(role);
      CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

      assertEquals(EXPECTED.get(role).contains(permission), user.has(permission),
          () -> role + " unexpectedly mapped " + permission);
    }
  }

  @Test
  void multipleRolesProduceASortedDeduplicatedUnion() {
    CedarUser user = userWithRoles(CedarUserRole.DEFAULT_USER, CedarUserRole.CATEGORY_ADMINISTRATOR,
        CedarUserRole.TEMPLATE_CREATOR);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    Set<CedarPermission> union = EnumSet.copyOf(EXPECTED.get(CedarUserRole.DEFAULT_USER));
    union.addAll(EXPECTED.get(CedarUserRole.CATEGORY_ADMINISTRATOR));
    union.addAll(EXPECTED.get(CedarUserRole.TEMPLATE_CREATOR));
    assertEquals(permissionNames(union), user.getPermissions());
    assertEquals(user.getPermissions().size(), new HashSet<>(user.getPermissions()).size());
  }

  @Test
  void duplicateRolesDoNotDuplicatePermissions() {
    CedarUser user = userWithRoles(CedarUserRole.METADATA_CREATOR, CedarUserRole.METADATA_CREATOR);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertEquals(permissionNames(EXPECTED.get(CedarUserRole.METADATA_CREATOR)), user.getPermissions());
  }

  @Test
  void nullRoleEntriesAreIgnored() {
    CedarUser user = userWithRoles(CedarUserRole.DEFAULT_USER, null, CedarUserRole.MONITOR_MANAGER);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    Set<CedarPermission> expected = EnumSet.copyOf(EXPECTED.get(CedarUserRole.DEFAULT_USER));
    expected.addAll(EXPECTED.get(CedarUserRole.MONITOR_MANAGER));
    assertEquals(permissionNames(expected), user.getPermissions());
  }

  @Test
  void nullRoleListIsTreatedAsNoRoles() {
    CedarUser user = new CedarUser();
    user.setRoles(null);
    user.setPermissions(List.of(CedarPermission.LOGGED_IN.getPermissionName()));

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertTrue(user.getPermissions().isEmpty());
    assertFalse(user.has(CedarPermission.LOGGED_IN));
  }

  @Test
  void emptyRolesClearPreviouslyExpandedPermissions() {
    CedarUser user = userWithRoles(CedarUserRole.FILESYSTEM_ADMINISTRATOR);
    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);
    assertFalse(user.getPermissions().isEmpty());
    user.setRoles(List.of());

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertTrue(user.getPermissions().isEmpty());
  }

  @Test
  void expansionRebuildsThePermissionCacheUsedByHas() {
    CedarUser user = userWithRoles(CedarUserRole.DEFAULT_USER);
    user.setPermissions(List.of(CedarPermission.MONITOR_READ.getPermissionName()));
    assertTrue(user.has(CedarPermission.MONITOR_READ));

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertTrue(user.has(CedarPermission.LOGGED_IN));
    assertTrue(user.has(CedarPermission.CATEGORY_READ));
    assertFalse(user.has(CedarPermission.MONITOR_READ));
    assertFalse(user.has(null));
  }

  @Test
  void expansionDoesNotMutateTheRoleList() {
    List<CedarUserRole> roles = new ArrayList<>(List.of(
        CedarUserRole.GROUP_ADMINISTRATOR, CedarUserRole.GROUP_PRIVILEGED_ADMINISTRATOR));
    CedarUser user = new CedarUser();
    user.setRoles(roles);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);

    assertEquals(List.of(CedarUserRole.GROUP_ADMINISTRATOR,
        CedarUserRole.GROUP_PRIVILEGED_ADMINISTRATOR), roles);
  }

  @Test
  void ordinaryGroupAdministratorDoesNotReceiveThePrivilegedOverride() {
    CedarUser ordinary = userWithRoles(CedarUserRole.GROUP_ADMINISTRATOR);
    CedarUser privileged = userWithRoles(CedarUserRole.GROUP_PRIVILEGED_ADMINISTRATOR);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(ordinary);
    CedarUserRolePermissionUtil.expandRolesIntoPermissions(privileged);

    assertFalse(ordinary.has(CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP));
    assertTrue(privileged.has(CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP));
  }

  @Test
  void ordinaryCategoryAdministratorDoesNotReceivePrivilegedOverrides() {
    CedarUser ordinary = userWithRoles(CedarUserRole.CATEGORY_ADMINISTRATOR);
    CedarUser privileged = userWithRoles(CedarUserRole.CATEGORY_PRIVILEGED_ADMINISTRATOR);

    CedarUserRolePermissionUtil.expandRolesIntoPermissions(ordinary);
    CedarUserRolePermissionUtil.expandRolesIntoPermissions(privileged);

    assertFalse(ordinary.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY));
    assertFalse(ordinary.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY));
    assertTrue(privileged.has(CedarPermission.WRITE_NOT_WRITABLE_CATEGORY));
    assertTrue(privileged.has(CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY));
  }

  private static Stream<Arguments> rolesAndExpectedPermissions() {
    return EXPECTED.entrySet().stream().map(e -> Arguments.of(e.getKey(), e.getValue()));
  }

  private static CedarUser userWithRoles(CedarUserRole... roles) {
    CedarUser user = new CedarUser();
    user.setRoles(Arrays.asList(roles));
    return user;
  }

  private static List<String> permissionNames(Set<CedarPermission> permissions) {
    return permissions.stream().map(CedarPermission::getPermissionName).sorted().toList();
  }

  private static Map<CedarUserRole, Set<CedarPermission>> expectedRoleMap() {
    Map<CedarUserRole, Set<CedarPermission>> expected = new EnumMap<>(CedarUserRole.class);
    expected.put(CedarUserRole.DEFAULT_USER, EnumSet.of(
        CedarPermission.LOGGED_IN, CedarPermission.CATEGORY_READ));
    expected.put(CedarUserRole.TEMPLATE_CREATOR, EnumSet.of(
        CedarPermission.TEMPLATE_FIELD_CREATE, CedarPermission.TEMPLATE_FIELD_READ,
        CedarPermission.TEMPLATE_FIELD_UPDATE, CedarPermission.TEMPLATE_FIELD_DELETE,
        CedarPermission.TEMPLATE_ELEMENT_CREATE, CedarPermission.TEMPLATE_ELEMENT_READ,
        CedarPermission.TEMPLATE_ELEMENT_UPDATE, CedarPermission.TEMPLATE_ELEMENT_DELETE,
        CedarPermission.TEMPLATE_CREATE, CedarPermission.TEMPLATE_READ,
        CedarPermission.TEMPLATE_UPDATE, CedarPermission.TEMPLATE_DELETE,
        CedarPermission.FOLDER_CREATE, CedarPermission.FOLDER_READ,
        CedarPermission.FOLDER_UPDATE, CedarPermission.FOLDER_DELETE));
    expected.put(CedarUserRole.METADATA_CREATOR, EnumSet.of(
        CedarPermission.TEMPLATE_INSTANCE_CREATE, CedarPermission.TEMPLATE_INSTANCE_READ,
        CedarPermission.TEMPLATE_INSTANCE_UPDATE, CedarPermission.TEMPLATE_INSTANCE_DELETE));
    expected.put(CedarUserRole.USER_ADMINISTRATOR, EnumSet.of(
        CedarPermission.USER_READ, CedarPermission.USER_UPDATE));
    expected.put(CedarUserRole.GROUP_ADMINISTRATOR, EnumSet.of(
        CedarPermission.GROUP_CREATE, CedarPermission.GROUP_READ,
        CedarPermission.GROUP_UPDATE, CedarPermission.GROUP_DELETE));
    expected.put(CedarUserRole.GROUP_PRIVILEGED_ADMINISTRATOR, EnumSet.of(
        CedarPermission.UPDATE_NOT_ADMINISTERED_GROUP));
    expected.put(CedarUserRole.FILESYSTEM_ADMINISTRATOR, EnumSet.of(
        CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_NODE,
        CedarPermission.READ_NOT_READABLE_NODE, CedarPermission.WRITE_NOT_WRITABLE_NODE));
    expected.put(CedarUserRole.CATEGORY_ADMINISTRATOR, EnumSet.of(
        CedarPermission.CATEGORY_CREATE, CedarPermission.CATEGORY_READ,
        CedarPermission.CATEGORY_UPDATE, CedarPermission.CATEGORY_DELETE));
    expected.put(CedarUserRole.CATEGORY_PRIVILEGED_ADMINISTRATOR, EnumSet.of(
        CedarPermission.UPDATE_PERMISSION_NOT_WRITABLE_CATEGORY,
        CedarPermission.WRITE_NOT_WRITABLE_CATEGORY));
    expected.put(CedarUserRole.ARTIFACT_PRIVILEGED_ADMINISTRATOR, EnumSet.of(
        CedarPermission.WRITE_ARTIFACT_VERBATIM));
    expected.put(CedarUserRole.SEARCH_REINDEXER, EnumSet.of(
        CedarPermission.SEARCH_INDEX_REINDEX, CedarPermission.RULES_INDEX_REINDEX,
        CedarPermission.INCLUSION_SUBGRAPH_RECREATE));
    expected.put(CedarUserRole.PROCESS_MESSAGE_SENDER, EnumSet.of(
        CedarPermission.SEND_PROCESS_MESSAGE));
    expected.put(CedarUserRole.MONITOR_MANAGER, EnumSet.of(CedarPermission.MONITOR_READ));
    return expected;
  }
}
