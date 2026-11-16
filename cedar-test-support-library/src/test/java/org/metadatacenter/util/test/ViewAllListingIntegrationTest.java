package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.result.BackendCallResult;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUser;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionUserPermissionPair;
import org.metadatacenter.server.security.model.permission.resource.ResourcePermissionsRequest;
import org.metadatacenter.server.security.model.permission.resource.ResourceRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The listing behind a search with no term, which reads the graph rather than the index. It shows a
 * caller what they may read and nothing else, and the count beside it, which sets the total and the
 * paging links, counts the same rows under every version and publication filter.
 */
@Tag("integration")
public class ViewAllListingIntegrationTest {

  private static final List<CedarResourceType> TEMPLATES = List.of(CedarResourceType.TEMPLATE);
  private static final List<String> SORT = List.of(QuerySortOptions.DEFAULT_SORT_FIELD.getName());

  private static CedarConfig cedarConfig;
  private static CedarUser user1;
  private static CedarUser user2;
  private static CedarRequestContext user1Context;
  private static CedarRequestContext user2Context;
  private static FolderServerArtifact unshared;
  private static FolderServerArtifact sharedDirectly;
  private static FolderServerArtifact sharedThroughFolder;
  private static FolderServerArtifact ownedByUser2;

  @BeforeAll
  public static void oneTimeSetUp() throws Exception {
    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of("CEDAR_REDIS_PERSISTENT_PORT", "1"));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_RESOURCE);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE));
    user1 = TestAuthUtil.getTestUser1(cedarConfig);
    user2 = TestAuthUtil.getTestUser2(cedarConfig);
    user1Context = CedarRequestContextFactory.fromUser(user1);
    user2Context = CedarRequestContextFactory.fromUser(user2);
    CedarFolderId user1Home = foldersOf(user1Context).findHomeFolderOf().getResourceId();
    CedarFolderId user2Home = foldersOf(user2Context).findHomeFolderOf().getResourceId();

    unshared = template(user1Context, "Listing unshared", user1Home, "bibo:draft");
    sharedDirectly = template(user1Context, "Listing shared directly", user1Home, "bibo:published");
    FolderServerFolder sharedFolder = folder("Listing shared folder", user1Home);
    sharedThroughFolder = template(user1Context, "Listing shared through a folder", sharedFolder.getResourceId(),
        "bibo:draft");
    ownedByUser2 = template(user2Context, "Listing owned by user 2", user2Home, "bibo:published");
    grantToUser2(sharedDirectly);
    grantToUser2(sharedFolder);
  }

  @Test
  public void aCallerListsWhatTheyMayReadAndNothingElse() {
    Set<String> listed = ids(foldersOf(user2Context).viewAll(TEMPLATES, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, 10_000, 0, SORT));

    Assertions.assertTrue(listed.containsAll(Set.of(sharedDirectly.getId(), sharedThroughFolder.getId(),
        ownedByUser2.getId())), listed.toString());
    Assertions.assertFalse(listed.contains(unshared.getId()), "another user's unshared template was listed");
  }

  @Test
  public void theAdministratorListsEveryTemplate() throws Exception {
    CedarRequestContext adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));
    Set<String> listed = ids(foldersOf(adminContext).viewAll(TEMPLATES, ResourceVersionFilter.ALL,
        ResourcePublicationStatusFilter.ALL, 10_000, 0, SORT));

    Assertions.assertTrue(listed.containsAll(Set.of(unshared.getId(), sharedDirectly.getId(),
        sharedThroughFolder.getId(), ownedByUser2.getId())), listed.toString());
  }

  static Stream<Arguments> filters() {
    return Stream.of(ResourceVersionFilter.values()).flatMap(version ->
        Stream.of(ResourcePublicationStatusFilter.values()).map(status -> Arguments.of(version, status)));
  }

  /** The count and the listing agree for a reader and for the administrator, under every filter. */
  @ParameterizedTest
  @MethodSource("filters")
  public void theCountCountsTheRowsTheListingReturns(ResourceVersionFilter version, ResourcePublicationStatusFilter status)
      throws Exception {
    for (CedarRequestContext context : List.of(user2Context,
        CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig)))) {
      FolderServiceSession folders = foldersOf(context);
      List<FolderServerResourceExtract> listed = folders.viewAll(TEMPLATES, version, status, 10_000, 0, SORT);
      Assertions.assertEquals(listed.size(), folders.viewAllCount(TEMPLATES, version, status),
          context.getCedarUser().getId() + " " + version + " " + status);
      if (status == ResourcePublicationStatusFilter.PUBLISHED) {
        Assertions.assertFalse(ids(listed).contains(sharedThroughFolder.getId()), "a draft passed the published filter");
      }
      if (status == ResourcePublicationStatusFilter.DRAFT) {
        Assertions.assertFalse(ids(listed).contains(sharedDirectly.getId()), "a published template passed the draft filter");
      }
    }
  }

  private static FolderServiceSession foldersOf(CedarRequestContext context) {
    return CedarDataServices.getInstance().getFolderServiceSession(context);
  }

  private static Set<String> ids(List<FolderServerResourceExtract> listed) {
    return listed.stream().map(FolderServerResourceExtract::getId).collect(Collectors.toSet());
  }

  private static FolderServerArtifact template(CedarRequestContext owner, String name, CedarFolderId parent,
                                               String publicationStatus) {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName(name);
    template.setDescription("Created by ViewAllListingIntegrationTest");
    template.setVersion("0.0.1");
    template.setPublicationStatus(publicationStatus);
    template.setLatestVersion(true);
    template.setLatestDraftVersion(publicationStatus.equals("bibo:draft"));
    template.setLatestPublishedVersion(publicationStatus.equals("bibo:published"));
    FolderServerArtifact created = foldersOf(owner).createResourceAsChildOfId(template, parent);
    Assertions.assertNotNull(created, "The template '" + name + "' should be created");
    return created;
  }

  private static FolderServerFolder folder(String name, CedarFolderId parent) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Created by ViewAllListingIntegrationTest");
    FolderServerFolder created = foldersOf(user1Context).createFolderAsChildOfId(folder, parent,
        cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class));
    Assertions.assertNotNull(created, "The folder '" + name + "' should be created");
    return created;
  }

  private static void grantToUser2(FileSystemResource resource) {
    ResourcePermissionsRequest request = new ResourcePermissionsRequest();
    request.setOwner(new ResourcePermissionUser(user1.getId()));
    request.getUserPermissions().add(new ResourcePermissionUserPermissionPair(
        new ResourcePermissionUser(user2.getId()), ResourceRole.VIEWER));
    BackendCallResult result = CedarDataServices.getInstance().getResourcePermissionServiceSession(user1Context)
        .updateResourcePermissions(resource.getResourceId(), request);
    Assertions.assertFalse(result.isError(), "the grant should succeed: " + result);
  }
}
