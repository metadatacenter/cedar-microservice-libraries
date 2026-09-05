package org.metadatacenter.util.http;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Map;

/**
 * Documentation model for the error envelope returned by CEDAR REST services.
 *
 * <p>The runtime still renders errors through {@link CedarResponse} and the exception mappers. This
 * class deliberately has no runtime role: it gives OpenAPI one common, permissive schema for the
 * fields those paths expose. A contract test derives the real enum wire values and keeps the
 * documented lists from drifting from the implementation.</p>
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
          "noWriteAccessToCategory", "folderNotFound", "artifactNotFound", "artifactPreconditionRequired",
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
          "groupCanBeModifiedOnlyByGroupAdmin", "groupCanBeDeletedOnlyByGroupAdmin", "groupUsersNotUpdated",
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
  public Map<String, Object> operation;

  @Schema(description = "Correlation identifier for an internal error recorded in server logs.",
      format = "uuid", nullable = true)
  public String errorId;

  private CedarError() {
  }
}
