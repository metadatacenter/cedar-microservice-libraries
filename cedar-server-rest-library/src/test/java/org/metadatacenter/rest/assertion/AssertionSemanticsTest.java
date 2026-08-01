package org.metadatacenter.rest.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.CedarAssertionNoun;
import org.metadatacenter.rest.assertion.noun.CedarParameterImpl;
import org.metadatacenter.rest.assertion.noun.CedarRequestNoun;
import org.metadatacenter.rest.assertion.noun.CedarUserNoun;
import org.metadatacenter.rest.context.CedarParameterSource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.HttpRequestEmptyBody;
import org.metadatacenter.rest.context.HttpRequestJsonBody;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.metadatacenter.constant.HttpConstants.CONTENT_TYPE_APPLICATION_MERGE_PATCH_JSON;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssertionSemanticsTest {

  private CedarRequestContext context;

  @BeforeEach
  void setUp() {
    context = mock(CedarRequestContext.class);
  }

  @ParameterizedTest
  @ValueSource(strings = {"x", "0", "false", " x ", "https://repo.example/id", "\u00a0value\u00a0"})
  void nonEmptyAcceptsEveryNonBlankParameterRepresentation(String value) {
    assertNull(new NonEmptyAssertion().check(context, parameter(JsonMapper.MAPPER.getNodeFactory().textNode(value))));
  }

  static Stream<Arguments> emptyParameterNodes() {
    return Stream.of(
        Arguments.of((JsonNode) null),
        Arguments.of(JsonMapper.MAPPER.nullNode()),
        Arguments.of(JsonMapper.MAPPER.missingNode()),
        Arguments.of(JsonMapper.MAPPER.getNodeFactory().textNode("")),
        Arguments.of(JsonMapper.MAPPER.getNodeFactory().textNode(" ")),
        Arguments.of(JsonMapper.MAPPER.getNodeFactory().textNode("\t\n"))
    );
  }

  @ParameterizedTest
  @MethodSource("emptyParameterNodes")
  void nonEmptyRejectsMissingNullAndBlankParametersAsBadRequests(JsonNode node) {
    CedarAssertionResult result = new NonEmptyAssertion().check(context, parameter(node));
    assertStatus(result, CedarResponseStatus.BAD_REQUEST);
    assertEquals("parameter", result.getErrorPack().getParameters().get("name"));
    assertEquals(CedarParameterSource.JsonBody, result.getErrorPack().getParameters().get("source"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"{}", "{\"name\":\"x\"}", "{\"nested\":{}}"})
  void nonEmptyAcceptsJsonObjectBodies(String json) throws Exception {
    assertNull(new NonEmptyAssertion().check(context, new HttpRequestJsonBody(JsonMapper.MAPPER.readTree(json))));
  }

  static Stream<Arguments> invalidBodies() throws Exception {
    return Stream.of(
        Arguments.of(new HttpRequestEmptyBody()),
        Arguments.of(new HttpRequestJsonBody()),
        Arguments.of(new HttpRequestJsonBody(null)),
        Arguments.of(new HttpRequestJsonBody(JsonMapper.MAPPER.nullNode())),
        Arguments.of(new HttpRequestJsonBody(JsonMapper.MAPPER.missingNode())),
        Arguments.of(new HttpRequestJsonBody(JsonMapper.MAPPER.readTree("[]"))),
        Arguments.of(new HttpRequestJsonBody(JsonMapper.MAPPER.getNodeFactory().textNode("value")))
    );
  }

  @ParameterizedTest
  @MethodSource("invalidBodies")
  void nonEmptyRejectsAbsentNullAndNonObjectBodiesAsBadRequests(CedarAssertionNoun body) {
    assertStatus(new NonEmptyAssertion().check(context, body), CedarResponseStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "https://example.org", "http://example.org/path", "https://repo.metadatacenter.org/templates/1",
      "https://example.org/a?x=1", "https://example.org/a#fragment", "https://sub.example.org:8443/a",
      "https://example.org/a%20b", "https://example.orgx/path"
  })
  void validUrlAcceptsSupportedAbsoluteHttpUrls(String url) {
    assertNull(new ValidUrlAssertion().check(context, url));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "example.org", "/relative", "ftp://example.org/a", "javascript:alert(1)",
      "https://", "https://exa mple.org", "https://example.org:bad/a", "file:///tmp/a", "mailto:a@example.org"})
  void validUrlRejectsMalformedRelativeAndUnsupportedUrlsAsBadRequests(String url) {
    assertStatus(new ValidUrlAssertion().check(context, url), CedarResponseStatus.BAD_REQUEST);
  }

  static Stream<Arguments> nonStringIds() {
    return Stream.of(Arguments.of((Object) null), Arguments.of(42), Arguments.of(true),
        Arguments.of(URI.create("https://example.org")), Arguments.of(List.of("id")));
  }

  @ParameterizedTest
  @MethodSource("nonStringIds")
  void validUrlRejectsNullAndNonStringValuesAsBadRequests(Object value) {
    assertStatus(new ValidUrlAssertion().check(context, value), CedarResponseStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(strings = {"id-1", "https://repo.example/templates/1", "urn:uuid:123", "tmp-1", "opaque"})
  void validIdDelegatesAcceptedStringsToLinkedDataPolicy(String id) {
    LinkedDataUtil linkedData = mock(LinkedDataUtil.class);
    when(context.getLinkedDataUtil()).thenReturn(linkedData);
    when(linkedData.isValidId(id)).thenReturn(true);

    assertNull(new ValidIdAssertion().check(context, id));
    verify(linkedData).isValidId(id);
  }

  @ParameterizedTest
  @ValueSource(strings = {"", " ", "bad", "relative/id", "https://wrong.example/id"})
  void validIdReturnsBadRequestWhenLinkedDataPolicyRejectsString(String id) {
    LinkedDataUtil linkedData = mock(LinkedDataUtil.class);
    when(context.getLinkedDataUtil()).thenReturn(linkedData);
    when(linkedData.isValidId(id)).thenReturn(false);

    assertStatus(new ValidIdAssertion().check(context, id), CedarResponseStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @MethodSource("nonStringIds")
  void validIdRejectsNullAndNonStringValuesBeforeConsultingPolicy(Object value) {
    assertStatus(new ValidIdAssertion().check(context, value), CedarResponseStatus.BAD_REQUEST);
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "application/merge-patch+json",
      "application/merge-patch+json; charset=UTF-8",
      "application/merge-patch+json;charset=utf-8",
      " Application/Merge-Patch+Json ",
      "APPLICATION/MERGE-PATCH+JSON; profile=example"
  })
  void mergePatchAcceptsCaseAndParametersAllowedByContentTypeSyntax(String contentType) {
    CedarRequestNoun request = request(contentType);
    assertNull(new JsonMergePatchAssertion().check(context, request));
  }

  @ParameterizedTest
  @MethodSource("invalidMergePatchTypes")
  void mergePatchRejectsMissingAndOtherMediaTypesAsBadRequests(String contentType) {
    assertStatus(new JsonMergePatchAssertion().check(context, request(contentType)), CedarResponseStatus.BAD_REQUEST);
  }

  static Stream<Arguments> invalidMergePatchTypes() {
    return Stream.of(Arguments.of((String) null), Arguments.of(""), Arguments.of("application/json"),
        Arguments.of("application/json-patch+json"), Arguments.of("text/plain"));
  }

  @Test
  void loggedInAcceptsUserWithLoggedInPermission() {
    CedarUser user = userWith(CedarPermission.LOGGED_IN);
    assertNull(new LoggedInAssertion().check(context, new CedarUserNoun(user)));
  }

  @Test
  void loggedInRejectsAuthenticatedUserWithoutMarkerAsForbidden() {
    CedarUser user = userWith(CedarPermission.TEMPLATE_READ);
    assertStatus(new LoggedInAssertion().check(context, new CedarUserNoun(user)), CedarResponseStatus.FORBIDDEN);
  }

  @Test
  void loggedInRejectsNullPermissionListAsForbidden() {
    CedarUser user = mock(CedarUser.class);
    when(user.getPermissions()).thenReturn(null);
    assertStatus(new LoggedInAssertion().check(context, new CedarUserNoun(user)), CedarResponseStatus.FORBIDDEN);
  }

  @Test
  void loggedInRejectsMissingUserAsUnauthorizedWithSourceException() {
    CedarAssertionResult result = new LoggedInAssertion().check(context, new CedarUserNoun(null));
    assertStatus(result, CedarResponseStatus.UNAUTHORIZED);
    assertNotNull(result.getErrorPack().getSourceException());
  }

  @ParameterizedTest
  @MethodSource("permissionChecks")
  void permissionCheckerDistinguishesPresentMissingAndInvalidInputs(CedarUser user, CedarPermission permission,
                                                                    CedarResponseStatus expectedStatus) {
    CedarAssertionResult result = PermissionChecker.check(new CedarUserNoun(user), permission);
    if (expectedStatus == null) {
      assertNull(result);
    } else {
      assertStatus(result, expectedStatus);
    }
  }

  static Stream<Arguments> permissionChecks() {
    CedarUser allowed = userWith(CedarPermission.TEMPLATE_READ);
    CedarUser denied = userWith(CedarPermission.FOLDER_READ);
    CedarUser nullPermissions = mock(CedarUser.class);
    when(nullPermissions.getPermissions()).thenReturn(null);
    return Stream.of(
        Arguments.of(allowed, CedarPermission.TEMPLATE_READ, null),
        Arguments.of(denied, CedarPermission.TEMPLATE_READ, CedarResponseStatus.FORBIDDEN),
        Arguments.of(nullPermissions, CedarPermission.TEMPLATE_READ, CedarResponseStatus.FORBIDDEN),
        Arguments.of(null, CedarPermission.TEMPLATE_READ, CedarResponseStatus.INTERNAL_SERVER_ERROR),
        Arguments.of(allowed, null, CedarResponseStatus.INTERNAL_SERVER_ERROR));
  }

  static Stream<Arguments> trueValues() {
    return Stream.of(Arguments.of(true, true), Arguments.of(false, false), Arguments.of((Object) null, false),
        Arguments.of("true", false), Arguments.of(1, false), Arguments.of(new Object(), false));
  }

  @ParameterizedTest
  @MethodSource("trueValues")
  void trueAssertionAcceptsOnlyBooleanTrue(Object value, boolean accepted) {
    CedarAssertionResult result = new TrueAssertion().check(context, value);
    assertEquals(accepted, result == null);
  }

  @Test
  void nullAndNonNullAssertionsAreExactInversesForPojoTargets() {
    Object value = new Object();
    assertNull(new NullAssertion().check(context, (Object) null));
    assertNotNull(new NullAssertion().check(context, value));
    assertNull(new NonNullAssertion().check(context, value));
    assertNotNull(new NonNullAssertion().check(context, (Object) null));
  }

  @Test
  void parameterNullnessDistinguishesMissingExplicitNullAndPresentValue() {
    CedarParameterImpl missing = parameter(null);
    CedarParameterImpl explicitNull = parameter(JsonMapper.MAPPER.nullNode());
    CedarParameterImpl value = parameter(JsonMapper.MAPPER.getNodeFactory().textNode("value"));

    assertNull(new NullAssertion().check(context, missing));
    assertNull(new NullAssertion().check(context, explicitNull));
    assertNotNull(new NullAssertion().check(context, value));
    assertNotNull(new NonNullAssertion().check(context, missing));
    assertNotNull(new NonNullAssertion().check(context, explicitNull));
    assertNull(new NonNullAssertion().check(context, value));
  }

  private static CedarParameterImpl parameter(JsonNode value) {
    CedarParameterImpl parameter = new CedarParameterImpl("parameter", CedarParameterSource.JsonBody);
    if (value != null) {
      parameter.setJsonNode(value);
    }
    return parameter;
  }

  private static CedarRequestNoun request(String contentType) {
    CedarRequestNoun request = mock(CedarRequestNoun.class);
    when(request.getContentType()).thenReturn(contentType);
    return request;
  }

  private static CedarUser userWith(CedarPermission... permissions) {
    CedarUser user = new CedarUser();
    user.setPermissions(Stream.of(permissions).map(CedarPermission::getPermissionName).toList());
    return user;
  }

  private static void assertStatus(CedarAssertionResult result, CedarResponseStatus status) {
    assertNotNull(result);
    assertSame(status, result.getErrorPack().getStatus());
  }
}
