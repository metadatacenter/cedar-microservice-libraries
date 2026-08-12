package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.auth.CedarGroupUser;
import org.metadatacenter.server.security.model.auth.CedarGroupUserRequest;
import org.metadatacenter.server.security.model.auth.CedarGroupUsersRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;

import java.util.List;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Hermetic validation matrix for replacement-style group membership requests. */
class GroupUsersRequestValidatorTest {

  private static final CedarGroupId GROUP_ID = CedarGroupId.build("group-1");
  private static final String USER_1 = "user-1";
  private static final String USER_2 = "user-2";

  @Test
  void missingGroupWinsBeforeAuthorizationOrBodyValidation() {
    Fixture f = new Fixture();
    when(f.service.findGroupById(GROUP_ID)).thenReturn(null);

    GroupUsersRequestValidator validator = f.validate(null);

    assertError(validator, CedarErrorKey.GROUP_NOT_FOUND);
    verify(f.service, never()).userCanAdministerGroup(GROUP_ID);
  }

  /**
   * The status is asserted, not only the key. GroupsResource answers its own 403 before this check is
   * reached, so nothing at the endpoint would notice the denial being reported as 401 — which is what
   * it was, telling a caller who is already identified to authenticate again.
   */
  @Test
  void unauthorizedCallerIsRejectedBeforeBodyValidation() {
    Fixture f = new Fixture();
    when(f.service.userCanAdministerGroup(GROUP_ID)).thenReturn(false);

    GroupUsersRequestValidator validator = f.validate(null);

    assertError(validator, CedarErrorKey.GROUP_CAN_BY_MODIFIED_ONLY_BY_GROUP_ADMIN);
    assertEquals(CedarResponseStatus.FORBIDDEN,
        validator.getCallResult().getFirstError().getErrorPack().getStatus());
  }

  @Test
  void authorizedEmptyReplacementIsValid() {
    Fixture f = new Fixture();

    GroupUsersRequestValidator validator = f.validate(new CedarGroupUsersRequest());

    assertTrue(validator.getCallResult().isOk());
    assertTrue(validator.getUsers().getUsers().isEmpty());
  }

  @Test
  void nullRequestReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    assertError(f.validate(null), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void nullUsersListReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    CedarGroupUsersRequest request = new CedarGroupUsersRequest();
    request.setUsers(null);
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void nullListEntryReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    CedarGroupUsersRequest request = new CedarGroupUsersRequest();
    request.setUsers(Collections.singletonList(null));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void entryWithoutUserReturnsStructuredMissingParameter() {
    Fixture f = new Fixture();
    CedarGroupUsersRequest request = request(new CedarGroupUserRequest(null, true, true));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void entryRequiresNonBlankUserId(String userId) {
    Fixture f = new Fixture();
    CedarGroupUsersRequest request = request(entry(userId, true, true));
    assertError(f.validate(request), CedarErrorKey.MISSING_PARAMETER);
  }

  @Test
  void duplicateUserIdsAreRejectedEvenWhenFlagsDiffer() {
    Fixture f = new Fixture();
    CedarGroupUsersRequest request = request(
        entry(USER_1, true, false), entry(USER_1, false, true));

    assertError(f.validate(request), CedarErrorKey.UNIQUE_CONSTRAINT_COLLISION);
  }

  /**
   * A user the graph does not hold fails the request rather than being dropped from it. The relation
   * queries match on id and affect nothing when the node is absent, so an unvalidated request applied
   * every change it could and reported the whole of it as done.
   */
  @Test
  void unknownUserIsRejectedRatherThanSkipped() {
    Fixture f = new Fixture();
    when(f.service.userExists(CedarUserId.build(USER_2))).thenReturn(false);

    assertError(f.validate(request(entry(USER_1, true, true), entry(USER_2, false, true))),
        CedarErrorKey.USER_NOT_FOUND);
  }

  @ParameterizedTest
  @CsvSource({"true,true", "true,false", "false,true", "false,false"})
  void administratorAndMemberFlagsRemainIndependent(boolean administrator, boolean member) {
    Fixture f = new Fixture();

    GroupUsersRequestValidator validator = f.validate(request(entry(USER_1, administrator, member)));

    assertTrue(validator.getCallResult().isOk());
    CedarGroupUser result = validator.getUsers().getUsers().get(0);
    assertEquals(administrator, result.isAdministrator());
    assertEquals(member, result.isMember());
  }

  @Test
  void successfulValidationPreservesRequestOrder() {
    Fixture f = new Fixture();

    GroupUsersRequestValidator validator = f.validate(request(
        entry(USER_2, false, true), entry(USER_1, true, true)));

    assertTrue(validator.getCallResult().isOk());
    assertEquals(USER_2, validator.getUsers().getUsers().get(0).getUser().getId());
    assertEquals(USER_1, validator.getUsers().getUsers().get(1).getUser().getId());
  }

  @Test
  void materializedUsersAreDetachedFromRequestIdentityObjects() {
    Fixture f = new Fixture();
    ResourcePermissionUser identity = new ResourcePermissionUser(USER_1);
    CedarGroupUsersRequest request = request(new CedarGroupUserRequest(identity, true, true));
    GroupUsersRequestValidator validator = f.validate(request);

    identity.setId("changed");

    assertEquals(USER_1, validator.getUsers().getUsers().get(0).getUser().getId());
  }

  private static CedarGroupUserRequest entry(String userId, boolean administrator, boolean member) {
    return new CedarGroupUserRequest(new ResourcePermissionUser(userId), administrator, member);
  }

  private static CedarGroupUsersRequest request(CedarGroupUserRequest... entries) {
    CedarGroupUsersRequest request = new CedarGroupUsersRequest();
    request.getUsers().addAll(List.of(entries));
    return request;
  }

  private static void assertError(GroupUsersRequestValidator validator, CedarErrorKey expected) {
    BackendCallResult result = validator.getCallResult();
    assertTrue(result.isError());
    assertEquals(expected, result.getFirstError().getErrorPack().getErrorKey());
  }

  private static final class Fixture {
    private final Neo4JUserSessionGroupService service = mock(Neo4JUserSessionGroupService.class);

    private Fixture() {
      when(service.findGroupById(GROUP_ID)).thenReturn(mock(FolderServerGroup.class));
      when(service.userCanAdministerGroup(GROUP_ID)).thenReturn(true);
      // Every named user exists unless a test says otherwise: validation looks each one up, and a
      // fixture whose users were all absent would fail every case for the wrong reason.
      when(service.userExists(any())).thenReturn(true);
    }

    private GroupUsersRequestValidator validate(CedarGroupUsersRequest request) {
      return new GroupUsersRequestValidator(service, GROUP_ID, request);
    }
  }
}
