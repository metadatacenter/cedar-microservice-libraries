package org.metadatacenter.server.cache.user;

import com.google.common.base.Ticker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateInstanceExtract;
import org.metadatacenter.model.folderserver.report.FolderServerInstanceReport;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphElement;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphNode;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphResponse;
import org.metadatacenter.model.request.inclusionsubgraph.InclusionSubgraphTemplate;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.security.model.user.CedarUserSummary;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The display names a response carries for the creator, last updater and owner of every resource in
 * it. Each of the three ids is resolved on its own, so the users here are distinct and a name filed
 * under the wrong role is visible. The walks matter as much as the lookup: a resource's path, a
 * listing's entries, a report's versions and sources, and an inclusion subgraph at every depth are
 * what the Workbench shows beside the resource itself.
 */
public class ProvenanceNameUtilTest {

  private static final String CREATOR = "https://metadatacenter.org/users/creator";
  private static final String UPDATER = "https://metadatacenter.org/users/updater";
  private static final String OWNER = "https://metadatacenter.org/users/owner";
  private static final String UNKNOWN = "https://metadatacenter.org/users/unknown";

  private static final Map<String, String> SCREEN_NAMES = Map.of(
      CREATOR, "Ada Creator",
      UPDATER, "Ben Updater",
      OWNER, "Cy Owner");

  @BeforeEach
  public void knownUsers() {
    UserSummaryCache.buildForTesting(id -> {
      String screenName = SCREEN_NAMES.get(id);
      if (screenName == null) {
        return null;
      }
      CedarUserSummary summary = new CedarUserSummary();
      summary.setId(id);
      summary.setScreenName(screenName);
      return summary;
    }, Ticker.systemTicker(), 60);
  }

  @Test
  public void aResourceAndEachFolderOnItsPathAreNamedByRole() {
    FolderServerFolderExtract parent = users(new FolderServerFolderExtract());
    FolderServerFolder folder = new FolderServerFolder();
    folder.setCreatedBy(CREATOR);
    folder.setLastUpdatedBy(UPDATER);
    folder.setOwnedBy(OWNER);
    folder.setPathInfo(List.<FolderServerResourceExtract>of(parent));

    ProvenanceNameUtil.addProvenanceDisplayName(folder);

    assertEquals("Ada Creator", folder.getCreatedByUserName());
    assertEquals("Ben Updater", folder.getLastUpdatedByUserName());
    assertEquals("Cy Owner", folder.getOwnedByUserName());
    assertNamed(parent);
  }

  /** A user the user server cannot resolve leaves that one name absent; it does not fail the response. */
  @Test
  public void anUnresolvableUserLeavesOnlyThatNameAbsent() {
    FolderServerTemplateExtract template = new FolderServerTemplateExtract();
    template.setCreatedBy(UNKNOWN);
    template.setLastUpdatedBy(UPDATER);
    template.setOwnedBy(null);

    ProvenanceNameUtil.addProvenanceDisplayName(template);

    assertNull(template.getCreatedByUserName());
    assertEquals("Ben Updater", template.getLastUpdatedByUserName());
    assertNull(template.getOwnedByUserName());
  }

  @Test
  public void everyListingEntryAndPathFolderIsNamed() {
    FolderServerTemplateExtract entry = users(new FolderServerTemplateExtract());
    FolderServerFolderExtract pathFolder = users(new FolderServerFolderExtract());
    FolderServerNodeListResponse listing = new FolderServerNodeListResponse();
    listing.setResources(List.of(entry));
    listing.setPathInfo(List.<FolderServerResourceExtract>of(pathFolder));

    ProvenanceNameUtil.addProvenanceDisplayNames(listing);

    assertNamed(entry);
    assertNamed(pathFolder);
  }

  @Test
  public void anInstanceReportNamesItsVersionsPathSourceAndTemplate() {
    FolderServerTemplateInstanceExtract version = users(new FolderServerTemplateInstanceExtract());
    FolderServerFolderExtract pathFolder = users(new FolderServerFolderExtract());
    FolderServerTemplateInstanceExtract derivedFrom = users(new FolderServerTemplateInstanceExtract());
    FolderServerTemplateExtract isBasedOn = users(new FolderServerTemplateExtract());
    FolderServerInstanceReport report = new FolderServerInstanceReport();
    report.setVersions(List.<FolderServerArtifactExtract>of(version));
    report.setPathInfo(List.<FolderServerResourceExtract>of(pathFolder));
    report.setDerivedFromExtract(derivedFrom);
    report.setIsBasedOnExtract(isBasedOn);

    ProvenanceNameUtil.addProvenanceDisplayNames(report);

    assertNamed(version);
    assertNamed(pathFolder);
    assertNamed(derivedFrom);
    assertNamed(isBasedOn);
  }

  @Test
  public void anInclusionSubgraphIsNamedAtEveryDepth() {
    InclusionSubgraphTemplate nestedTemplate = users(new InclusionSubgraphTemplate());
    InclusionSubgraphElement nestedElement = users(new InclusionSubgraphElement());
    nestedElement.setTemplates(Map.of("t", nestedTemplate));
    InclusionSubgraphElement element = users(new InclusionSubgraphElement());
    element.setElements(Map.of("e", nestedElement));
    InclusionSubgraphTemplate template = users(new InclusionSubgraphTemplate());
    InclusionSubgraphResponse subgraph = new InclusionSubgraphResponse();
    subgraph.setElements(Map.of("e", element));
    subgraph.setTemplates(Map.of("t", template));

    ProvenanceNameUtil.addProvenanceDisplayNames(subgraph);

    for (InclusionSubgraphNode node : List.<InclusionSubgraphNode>of(element, nestedElement, nestedTemplate, template)) {
      assertEquals("Ada Creator", node.getCreatedByUserName());
      assertEquals("Ben Updater", node.getLastUpdatedByUserName());
      assertEquals("Cy Owner", node.getOwnedByUserName());
    }
  }

  private static <T extends FolderServerResourceExtract> T users(T extract) {
    extract.setCreatedBy(CREATOR);
    extract.setLastUpdatedBy(UPDATER);
    extract.setOwnedBy(OWNER);
    return extract;
  }

  private static <T extends InclusionSubgraphNode> T users(T node) {
    node.setCreatedBy(CREATOR);
    node.setLastUpdatedBy(UPDATER);
    node.setOwnedBy(OWNER);
    return node;
  }

  private static void assertNamed(FolderServerResourceExtract extract) {
    assertEquals("Ada Creator", extract.getCreatedByUserName());
    assertEquals("Ben Updater", extract.getLastUpdatedByUserName());
    assertEquals("Cy Owner", extract.getOwnedByUserName());
  }
}
