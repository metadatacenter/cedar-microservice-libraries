package org.metadatacenter.util.http;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.http.CedarResponseStatus;

import jakarta.ws.rs.core.Response;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The error envelope returned by CEDAR REST services.
 *
 * <p>This is both the runtime representation and the OpenAPI model. It is deliberately a superset
 * of the two historical response shapes: {@code message}/{@code errorMessage} and
 * {@code status}/{@code statusCode} remain paired aliases, while exception-only diagnostic fields
 * are available to responses built directly by a resource as well. Internal exception objects are
 * never copied into this client-facing type.</p>
 */
@Schema(name = "CedarError", description = "A CEDAR error response. Diagnostic fields are populated only "
    + "when they are relevant to the failure.", additionalProperties = Schema.AdditionalPropertiesValue.TRUE)
public final class CedarError {

  @Schema(description = "Symbolic HTTP response status.", requiredMode = Schema.RequiredMode.REQUIRED,
      allowableValues = {"BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND", "METHOD_NOT_ALLOWED",
          "NOT_ACCEPTABLE", "CONFLICT", "PRECONDITION_FAILED", "UNSUPPORTED_MEDIA_TYPE",
          "UNPROCESSABLE_ENTITY", "PRECONDITION_REQUIRED", "INTERNAL_SERVER_ERROR", "NOT_IMPLEMENTED",
          "BAD_GATEWAY", "SERVICE_UNAVAILABLE", "HTTP_VERSION_NOT_SUPPORTED"})
  public String status;

  @Schema(description = "Numeric HTTP response status.", minimum = "400", maximum = "599",
      requiredMode = Schema.RequiredMode.REQUIRED)
  public int statusCode;

  @Schema(description = "Stable machine-readable error identifier.", nullable = true,
      allowableValues = {
          "templateElementNotCreated", "templateElementNotFound", "templateElementNotDeleted",
          "templateElementNotUpdated", "templateElementsNotListed", "templateFieldNotCreated",
          "templateFieldNotFound", "templateFieldNotDeleted", "templateFieldNotUpdated",
          "templateFieldsNotListed", "templateNotCreated", "templateNotFound", "templateNotDeleted",
          "templateNotUpdated", "templatesNotListed", "templateInstanceNotCreated",
          "templateInstanceNotFound", "templateInstanceNotDeleted", "templateInstanceNotUpdated",
          "templateInstancesNotListed", "noReadAccessToResource", "noWriteAccessToResource",
          "noReadAccessToFolder", "noWriteAccessToFolder", "noReadAccessToTemplate",
          "noWriteAccessToTemplate", "noReadAccessToTemplateElement", "noWriteAccessToTemplateElement",
          "noReadAccessToTemplateField", "noWriteAccessToTemplateField", "noReadAccessToTemplateInstance",
          "noWriteAccessToTemplateInstance", "noReadAccessToArtifact", "noWriteAccessToArtifact",
          "noReadAccessToCategory", "noWriteAccessToCategory", "folderNotFound", "artifactNotFound",
          "artifactPreconditionRequired",
          "artifactHasMovedOn", "verbatimWriteRefused", "nodeNotFound", "unknownResourceType",
          "missingParameter", "invalidInput", "sourceFolderNotFound", "sourceResourceNotFound",
          "targetFolderNotFound", "nodeNotMoved", "groupNotFound", "userNotFound",
          "uniqueConstraintCollision", "invalidData", "notAuthorized", "permissionMissing", "tokenInvalid",
          "userInfoLoadByTokenFailed", "userInfoLoadByApiKeyFailed", "cedarUserNotFound",
          "authorizationNotFound", "permissionNotOwned", "authorizationTypeUnknown", "apiKeyNotFound",
          "tokenMissing", "tokenExpired", "parentFolderNotSpecified", "parentFolderSpecifiedTwice",
          "pathNotNormalized", "parentFolderNotFound", "updateInvalidFolderName", "createInvalidFolderName",
          "nodeAlreadyPresent", "folderNotCreated", "missingNameAndDescription", "missingData",
          "folderNotDeleted", "artifactNotDeleted", "resourceNotCreated", "invalidResourceType",
          "invalidArtifactType", "readOtherProfileForbidden", "updateOtherProfileForbidden",
          "folderCanNotBeDeleted", "folderCanNotBeChanged", "groupAlreadyPresent",
          "groupCanBeModifiedOnlyByGroupAdmin", "groupCanBeDeletedOnlyByGroupAdmin",
          "groupMembersCanBeReadOnlyByGroupAdmin",
          "groupRequiresAdministrator", "groupUsersNotUpdated",
          "specialGroupCanNotBeDeleted", "folderPermissionsCanNotBeChanged", "unknownInstanceOutputFormat",
          "folderCopyNotAllowed", "methodNotImplemented", "upstreamServerError", "nothingToDo",
          "parentCategoryNotFound", "categoryAlreadyPresent", "categoryNotFound", "categoryCanNotBeDeleted",
          "rootCategoryCanNotBeDeleted", "unableToAttachCategory", "unableToDetachCategory",
          "noCategoriesWereAttached", "malformedJsonRequestBody", "malformedSearchTerm",
          "publishedArtifactCanNotBeChanged", "publishedArtifactCanNotBeDeleted", "versioningOnlyOnLatest",
          "versioningOnlyByOwner", "nonVersionedArtifactType", "createDraftOnlyFromPublished",
          "publishOnlyDraft", "draftNotCreated", "contentNotValid", "doiNotSupportedByResourceType",
          "doiCanNotBeSetForEmptyAtId", "doiCanNotBeAltered", "doiCanNotBeSet", "doiAlreadyExists",
          "dataCiteDOIDisabled", "resourceNotFound", "internalError"
      })
  public String errorKey;

  @Schema(description = "More specific machine-readable reason for the error.", nullable = true,
      allowableValues = {"nonEmptyFolder", "nonEmptyCategory", "userHomeFolder", "systemFolder",
          "templateReferencedInInstances", "validationError", "continuationExpired"})
  public String errorReasonKey;

  @Schema(description = "Broad category of the error.", nullable = true,
      allowableValues = {"notFound", "invalidArgument", "authorization", "permission", "server",
          "validationError"})
  public String errorType;

  @Schema(description = "Human-readable error message.", nullable = true)
  public String message;

  @Schema(description = "Alias of message retained for compatibility.", nullable = true)
  public String errorMessage;

  @Schema(description = "Named scalar values associated with the error.")
  public Map<String, Object> parameters;

  @Schema(description = "Named structured values associated with the error.")
  public Map<String, Object> objects;

  @Schema(description = "Named domain entities associated with exception-mapped errors.", nullable = true)
  public Map<String, Object> entities;

  @Schema(description = "Recovery action suggested to the client.", nullable = true,
      allowableValues = {"none", "requestRole", "logout", "logoutImmediately", "provideAuthorizationHeader",
          "refreshToken"})
  public String suggestedAction;

  @Schema(description = "Operation that failed. Its fields depend on the operation type.", nullable = true)
  public Object operation;

  @Schema(description = "Correlation identifier for an internal error recorded in server logs.",
      format = "uuid", nullable = true)
  public String errorId;

  private final Map<String, Object> extensions = new LinkedHashMap<>();

  private CedarError() {
  }

  /** Build a client-safe envelope from the internal error accumulator. */
  public static CedarError from(CedarErrorPack pack, String errorId) {
    CedarError error = new CedarError();
    CedarResponseStatus responseStatus = pack.getStatus();
    error.status = responseStatus == null ? CedarResponseStatus.INTERNAL_SERVER_ERROR.name() : responseStatus.name();
    error.statusCode = pack.getStatusCode();
    error.errorKey = pack.getErrorKey() == null ? null : pack.getErrorKey().getValue();
    error.errorReasonKey = pack.getErrorReasonKey() == null ? null : pack.getErrorReasonKey().getValue();
    error.errorType = pack.getErrorType() == null ? null : pack.getErrorType().getValue();
    error.message = pack.getMessage();
    error.errorMessage = pack.getMessage();
    error.parameters = pack.getParameters();
    error.objects = pack.getObjects();
    error.entities = pack.getEntities();
    error.suggestedAction = pack.getSuggestedAction() == null ? null : pack.getSuggestedAction().getValue();
    error.operation = pack.getOperation() == null ? null : pack.getOperation().asJson();
    error.errorId = errorId;
    return error;
  }

  /** Build the envelope for a framework status that is not represented by {@link CedarResponseStatus}. */
  public static CedarError fromStatus(int statusCode) {
    CedarError error = new CedarError();
    Response.Status standardStatus = Response.Status.fromStatusCode(statusCode);
    error.status = standardStatus == null ? "HTTP_" + statusCode : standardStatus.name();
    error.statusCode = statusCode;
    error.parameters = Collections.emptyMap();
    error.objects = Collections.emptyMap();
    error.entities = Collections.emptyMap();
    return error;
  }

  /** Preserve an endpoint-specific legacy key while also emitting the canonical fields. */
  public CedarError extension(String name, Object value) {
    extensions.put(name, value);
    return this;
  }

  /** Preserve a legacy broad error type whose value predates the common enum. */
  public CedarError legacyErrorType(String value) {
    errorType = value;
    return this;
  }

  @JsonAnyGetter
  public Map<String, Object> extensions() {
    return extensions;
  }
}
