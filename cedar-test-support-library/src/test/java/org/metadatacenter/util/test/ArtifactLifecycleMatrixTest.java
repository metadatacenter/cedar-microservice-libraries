package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.error.CedarErrorKey;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUntypedSchemaArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.report.FolderServerArtifactReport;
import org.metadatacenter.model.folderserver.report.FolderServerSchemaArtifactReport;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.VersionServiceSession;
import org.metadatacenter.outcome.OutcomeWithReason;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.resource.FilesystemResourcePermission;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The artifact lifecycle rules, as a table: for each state an artifact can be in, what each
 * versioning operation must answer.
 *
 * <p>CEDAR enumerates these rules as error keys — PUBLISH_ONLY_DRAFT,
 * CREATE_DRAFT_ONLY_FROM_PUBLISHED, VERSIONING_ONLY_ON_LATEST, VERSIONING_ONLY_BY_OWNER,
 * NON_VERSIONED_ARTIFACT_TYPE — but nothing asserted them. They are invariants, which is the class
 * of rule that regresses silently: they are what stops a published artifact from being altered and
 * what keeps a version chain linear, and a caller only notices they have gone when data has already
 * been damaged. The three predicates on {@link VersionServiceSession} are pure decisions over graph
 * state, so the whole grid can be checked against an in-process Neo4j with no other backend.
 *
 * <p>The table also pins the <em>precedence</em> between rules, which is where the surprises live: a
 * published artifact that has been superseded answers PUBLISH_ONLY_DRAFT rather than
 * VERSIONING_ONLY_ON_LATEST, because publication status is checked before latest-ness. Read the rows
 * for the superseded states together to see it.
 *
 * <p>Two facts recorded here that are easy to misread from the code:
 * <ul>
 *   <li>Only {@code userCanPerformVersioning} checks ownership. {@code resourceCanBePublished} and
 *       {@code resourceCanBeDrafted} do not, so a caller must combine them — the REST layer does.</li>
 *   <li>Deleting a published artifact is <em>allowed</em>. The PUBLISHED_ARTIFACT_CAN_NOT_BE_DELETED
 *       guard in the resource server is commented out deliberately, by commit 3f26ee7 (2021-02-08)
 *       "Allow users to delete published resources", which leaves that error key vestigial. Only
 *       PUBLISHED_ARTIFACT_CAN_NOT_BE_CHANGED is still enforced. No row asserts delete here because
 *       it is an HTTP-layer concern, but the asymmetry is worth knowing when reading the keys.</li>
 * </ul>
 */
public class ArtifactLifecycleMatrixTest {

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static CedarFolderId user1HomeId;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    // The Redis redirection must be in place before the CedarConfig singleton is built.
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);

    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    CedarUser user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);
    user1HomeId = CedarDataServices.getInstance().getFolderServiceSession(user1Context).findHomeFolderOf().getResourceId();
  }

  /** Which lifecycle question is being asked. */
  private enum Operation {
    VERSION,   // userCanPerformVersioning
    PUBLISH,   // resourceCanBePublished
    DRAFT      // resourceCanBeDrafted
  }

  /**
   * One cell: asking {@code operation} about {@code artifact} as {@code asUser} must be allowed, or
   * must be refused with exactly {@code expectedReason}.
   */
  private record Cell(String state, Operation operation, FolderServerArtifact artifact,
                      CedarRequestContext asUser, CedarErrorKey expectedReason) {
  }

  @Test
  public void lifecycleRulesHoldForEveryState() {
    // ── the states ────────────────────────────────────────────────────────────────────────────
    FolderServerArtifact draftLatest = createTemplate("Lifecycle Draft Latest", "0.0.1", "bibo:draft", null);
    FolderServerArtifact publishedLatest = createTemplate("Lifecycle Published Latest", "1.0.0", "bibo:published", null);

    // A superseded artifact is one some later version points at. Creating the successor is what
    // establishes it, exactly as the resource server does when it versions an artifact.
    FolderServerArtifact publishedSuperseded =
        createTemplate("Lifecycle Published Superseded", "1.0.0", "bibo:published", null);
    createTemplate("Lifecycle Published Successor", "2.0.0", "bibo:draft", publishedSuperseded);

    FolderServerArtifact draftSuperseded = createTemplate("Lifecycle Draft Superseded", "0.0.1", "bibo:draft", null);
    createTemplate("Lifecycle Draft Successor", "0.0.2", "bibo:draft", draftSuperseded);

    FolderServerArtifact instance = createInstance("Lifecycle Instance");

    // ── the table ─────────────────────────────────────────────────────────────────────────────
    List<Cell> cells = new ArrayList<>();

    // A draft that nothing supersedes: it may be versioned and published, but a draft cannot be
    // drafted again.
    cells.add(new Cell("draft, latest", Operation.VERSION, draftLatest, user1Context, null));
    cells.add(new Cell("draft, latest", Operation.PUBLISH, draftLatest, user1Context, null));
    cells.add(new Cell("draft, latest", Operation.DRAFT, draftLatest, user1Context,
        CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED));

    // A published artifact that nothing supersedes: it may be drafted, but not published again.
    cells.add(new Cell("published, latest", Operation.VERSION, publishedLatest, user1Context, null));
    cells.add(new Cell("published, latest", Operation.PUBLISH, publishedLatest, user1Context,
        CedarErrorKey.PUBLISH_ONLY_DRAFT));
    cells.add(new Cell("published, latest", Operation.DRAFT, publishedLatest, user1Context, null));

    // Superseded and published: PUBLISH reports the status rule, not the latest rule, because status
    // is checked first. DRAFT gets past the status check and then reports the latest rule.
    cells.add(new Cell("published, superseded", Operation.PUBLISH, publishedSuperseded, user1Context,
        CedarErrorKey.PUBLISH_ONLY_DRAFT));
    cells.add(new Cell("published, superseded", Operation.DRAFT, publishedSuperseded, user1Context,
        CedarErrorKey.VERSIONING_ONLY_ON_LATEST));

    // Superseded and draft: the mirror image. PUBLISH passes the status check and reports the latest
    // rule; DRAFT fails on status first.
    cells.add(new Cell("draft, superseded", Operation.PUBLISH, draftSuperseded, user1Context,
        CedarErrorKey.VERSIONING_ONLY_ON_LATEST));
    cells.add(new Cell("draft, superseded", Operation.DRAFT, draftSuperseded, user1Context,
        CedarErrorKey.CREATE_DRAFT_ONLY_FROM_PUBLISHED));

    // Ownership is only consulted by VERSION. The other two predicates are deliberately silent about
    // who is asking, which is why the REST layer has to combine them.
    cells.add(new Cell("draft, latest, asked by a non-owner", Operation.VERSION, draftLatest, user2Context,
        CedarErrorKey.VERSIONING_ONLY_BY_OWNER));

    // Versioning is owner-only, and no grant changes that. FilesystemResourcePermission declares
    // PUBLISH and CREATE_DRAFT alongside READ and WRITE, but userCanPerformVersioning asks
    // userIsOwnerOfFilesystemResource and nothing else, and neither level appears anywhere in
    // production code. So granting PUBLISH cannot let the grantee publish, and granting CREATE_DRAFT
    // cannot let them create a draft: the levels name operations they do not confer.
    //
    // These rows demonstrate that rather than arguing it from the absence of references — the same
    // refusal arrives whether the grantee holds nothing, WRITE, PUBLISH or CREATE_DRAFT. If the levels
    // are ever enforced, these rows fail and should become allowances.
    FolderServerArtifact grantedWrite = createTemplate("Lifecycle Granted Write", "0.0.1", "bibo:draft", null);
    grantToUser2(grantedWrite, FilesystemResourcePermission.WRITE);
    FolderServerArtifact grantedPublish = createTemplate("Lifecycle Granted Publish", "0.0.1", "bibo:draft", null);
    grantToUser2(grantedPublish, FilesystemResourcePermission.PUBLISH);
    FolderServerArtifact grantedCreateDraft =
        createTemplate("Lifecycle Granted Create Draft", "1.0.0", "bibo:published", null);
    grantToUser2(grantedCreateDraft, FilesystemResourcePermission.CREATE_DRAFT);

    cells.add(new Cell("draft, latest, grantee holds WRITE", Operation.VERSION, grantedWrite, user2Context,
        CedarErrorKey.VERSIONING_ONLY_BY_OWNER));
    cells.add(new Cell("draft, latest, grantee holds PUBLISH", Operation.VERSION, grantedPublish, user2Context,
        CedarErrorKey.VERSIONING_ONLY_BY_OWNER));
    cells.add(new Cell("published, latest, grantee holds CREATE_DRAFT", Operation.VERSION, grantedCreateDraft,
        user2Context, CedarErrorKey.VERSIONING_ONLY_BY_OWNER));

    // Instances carry no version chain, so versioning them is refused on type alone.
    cells.add(new Cell("instance (a non-versioned type)", Operation.VERSION, instance, user1Context,
        CedarErrorKey.NON_VERSIONED_ARTIFACT_TYPE));

    // ── run it ────────────────────────────────────────────────────────────────────────────────
    Assertions.assertFalse(cells.isEmpty(), "The lifecycle table is empty, so it asserts nothing");
    StringBuilder failures = new StringBuilder();
    for (Cell cell : cells) {
      OutcomeWithReason outcome = ask(cell);
      String label = cell.state() + " / " + cell.operation();
      if (cell.expectedReason() == null) {
        if (!outcome.isPositive()) {
          failures.append(label).append(": expected to be allowed but was refused with ")
              .append(outcome.getReason()).append('\n');
        }
      } else if (outcome.isPositive()) {
        failures.append(label).append(": expected refusal ").append(cell.expectedReason())
            .append(" but it was allowed\n");
      } else if (outcome.getReason() != cell.expectedReason()) {
        failures.append(label).append(": expected refusal ").append(cell.expectedReason())
            .append(" but got ").append(outcome.getReason()).append('\n');
      }
    }
    Assertions.assertEquals(0, failures.length(), "Artifact lifecycle rules diverged:\n" + failures);
  }

  private OutcomeWithReason ask(Cell cell) {
    VersionServiceSession versions = CedarDataServices.getInstance().getVersionServiceSession(cell.asUser());
    return switch (cell.operation()) {
      // VERSION is asked about non-versioned artifacts too, since refusing those on type is one of
      // the rules under test. The general report factory covers instances as well as schema
      // artifacts, which is how the REST layer builds it (ArtifactReportUtil).
      case VERSION -> versions.userCanPerformVersioning(FolderServerArtifactReport.fromResource(cell.artifact()));
      // PUBLISH and DRAFT read the publication status, which only the schema-artifact report carries.
      case PUBLISH -> versions.resourceCanBePublished(FolderServerSchemaArtifactReport.fromResource(cell.artifact()));
      case DRAFT -> versions.resourceCanBeDrafted(FolderServerSchemaArtifactReport.fromResource(cell.artifact()));
    };
  }

  /**
   * Grants user 2 the given permission on the artifact, as its owner. The request replaces the whole
   * permission set, so it restates user 1 as owner; the validator rejects a request without one.
   */
  private void grantToUser2(FolderServerArtifact artifact, FilesystemResourcePermission permission) {
    CedarUser user2 = TestAuthUtil.getTestUser2(cedarConfig);
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), permission));
    BackendCallResult result = CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(artifact.getResourceId(), request);
    Assertions.assertFalse(result.isError(),
        "granting " + permission + " should succeed: "
            + (result.isError() ? result.getFirstErrorMessage() : ""));
  }

  /**
   * Creates a template under user 1's home folder. When {@code supersedes} is given, the new template
   * points at it as its previous version, which is what makes that earlier artifact superseded.
   */
  private FolderServerArtifact createTemplate(String name, String version, String status,
                                              FolderServerArtifact supersedes) {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName(name);
    template.setDescription("Created by ArtifactLifecycleMatrixTest");
    template.setVersion(version);
    template.setPublicationStatus(status);
    template.setLatestVersion(supersedes == null);
    template.setLatestDraftVersion("bibo:draft".equals(status));
    template.setLatestPublishedVersion("bibo:published".equals(status));
    if (supersedes != null) {
      template.setPreviousVersion(CedarUntypedSchemaArtifactId.build(supersedes.getId()));
    }
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1Context);
    FolderServerArtifact created = folders.createResourceAsChildOfId(template, user1HomeId);
    Assertions.assertNotNull(created, "The template '" + name + "' should be created");
    return created;
  }

  private FolderServerArtifact createInstance(String name) {
    FolderServerInstance instance = new FolderServerInstance();
    instance.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.INSTANCE));
    instance.setName(name);
    instance.setDescription("Created by ArtifactLifecycleMatrixTest");
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1Context);
    FolderServerArtifact created = folders.createResourceAsChildOfId(instance, user1HomeId);
    Assertions.assertNotNull(created, "The instance '" + name + "' should be created");
    return created;
  }

}
