package org.metadatacenter.server.neo4j.proxy;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarSchemaArtifactId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.server.neo4j.AbstractNeo4JUserSession;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissions;
import org.metadatacenter.server.security.model.auth.FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus;
import org.metadatacenter.server.security.model.user.CedarUser;

public class Neo4JUserSessionVersionService extends AbstractNeo4JUserSession implements VersionServiceSession {

  private Neo4JUserSessionVersionService(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cu, String globalRequestId, String localRequestId) {
    super(cedarConfig, proxies, cu, globalRequestId, localRequestId);
  }

  public static VersionServiceSession get(CedarConfig cedarConfig, Neo4JProxies proxies, CedarUser cedarUser, String globalRequestId,
                                          String localRequestId) {
    return new Neo4JUserSessionVersionService(cedarConfig, proxies, cedarUser, globalRequestId, localRequestId);
  }

  @Override
  public OutcomeWithReason userCanPerformVersioning(FilesystemResourceWithCurrentUserPermissions resource) {
    if (!userIsOwnerOfFilesystemResource(resource.getResourceId())) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_BY_OWNER);
    }
    if (!resource.getType().isVersioned()) {
      return OutcomeWithReason.negative(CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE);
    }
    return OutcomeWithReason.positive();
  }

  @Override
  public OutcomeWithReason resourceCanBePublished(FilesystemResourceWithCurrentUserPermissions resource) {
    if (resource instanceof FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus) {
      FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus res =
          (FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus) resource;
      if (res.getPublicationStatus() != BiboStatus.DRAFT) {
        return OutcomeWithReason.negative(CedarErrorKey.PUBLISH_ONLY_DRAFT);
      }
    }

    FileSystemResource nextVersion = proxies.version().resourceWithPreviousVersion(CedarSchemaArtifactId.build(resource.getId(), resource.getType()));
    if (nextVersion != null) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }
    return OutcomeWithReason.positive();
  }

  @Override
  public OutcomeWithReason resourceCanBeDrafted(FilesystemResourceWithCurrentUserPermissions resource) {
    if (resource instanceof FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus) {
      FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus res =
          (FilesystemResourceWithCurrentUserPermissionsAndPublicationStatus) resource;
      if (res.getPublicationStatus() != BiboStatus.PUBLISHED) {
        return OutcomeWithReason.negative(CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED);
      }
    }

    FileSystemResource nextVersion = proxies.version().resourceWithPreviousVersion(CedarSchemaArtifactId.build(resource.getId(), resource.getType()));
    if (nextVersion != null) {
      return OutcomeWithReason.negative(CedarErrorKey.VERSIONING_ONLY_ON_LATEST);
    }

    return OutcomeWithReason.positive();
  }

}
