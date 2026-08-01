package org.metadatacenter.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.extract.FolderServerArtifactExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerElementExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerFieldExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerFolderExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateExtract;
import org.metadatacenter.model.folderserver.extract.FolderServerTemplateInstanceExtract;
import org.metadatacenter.model.folderserver.report.FolderServerTemplateReport;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TrustedByUtilTest {

  private static final String TRUSTED_FOLDER = "folder-trusted";
  private static final Map<String, String> TRUST = Map.of(TRUSTED_FOLDER, "BioPortal");

  static Stream<Arguments> artifactExtractTypes() {
    return Stream.of(
        Arguments.of(CedarResourceType.FIELD, (Supplier<FolderServerArtifactExtract>) FolderServerFieldExtract::new),
        Arguments.of(CedarResourceType.ELEMENT, (Supplier<FolderServerArtifactExtract>) FolderServerElementExtract::new),
        Arguments.of(CedarResourceType.TEMPLATE, (Supplier<FolderServerArtifactExtract>) FolderServerTemplateExtract::new),
        Arguments.of(CedarResourceType.INSTANCE, (Supplier<FolderServerArtifactExtract>) FolderServerTemplateInstanceExtract::new)
    );
  }

  @ParameterizedTest
  @MethodSource("artifactExtractTypes")
  void assignsTrustToEveryArtifactTypeUnderConfiguredFolder(CedarResourceType expectedType,
                                                             Supplier<FolderServerArtifactExtract> factory) {
    FolderServerArtifactExtract artifact = factory.get();

    TrustedByUtil.decorateWithTrustedBy(artifact, TRUSTED_FOLDER, TRUST);

    assertEquals(expectedType, artifact.getType());
    assertEquals("BioPortal", artifact.getTrustedBy());
  }

  @ParameterizedTest
  @MethodSource("artifactExtractTypes")
  void leavesEveryArtifactTypeUntrustedOutsideConfiguredFolder(CedarResourceType ignoredType,
                                                                Supplier<FolderServerArtifactExtract> factory) {
    FolderServerArtifactExtract artifact = factory.get();

    TrustedByUtil.decorateWithTrustedBy(artifact, "ordinary-folder", TRUST);

    assertNull(artifact.getTrustedBy());
  }

  @ParameterizedTest
  @MethodSource("artifactExtractTypes")
  void nullTrustConfigurationSafelyLeavesArtifactsUntrusted(CedarResourceType ignoredType,
                                                             Supplier<FolderServerArtifactExtract> factory) {
    FolderServerArtifactExtract artifact = factory.get();

    TrustedByUtil.decorateWithTrustedBy(artifact, TRUSTED_FOLDER, null);

    assertNull(artifact.getTrustedBy());
  }

  @Test
  void folderExtractIsNeverCastOrDecoratedAsArtifact() {
    FolderServerFolderExtract folder = folder(TRUSTED_FOLDER);
    assertDoesNotThrow(() -> TrustedByUtil.decorateWithTrustedBy(folder, TRUSTED_FOLDER, TRUST));
  }

  @Test
  void pathEndingAtFolderUsesThatFolderAsParent() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract();
    List<FolderServerResourceExtract> path = List.of(folder("root"), folder(TRUSTED_FOLDER));

    TrustedByUtil.decorateWithTrustedBy(artifact, path, TRUST);

    assertEquals("BioPortal", artifact.getTrustedBy());
  }

  @Test
  void pathEndingAtArtifactUsesPenultimateFolderAsParent() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract(); artifact.setId("template");
    List<FolderServerResourceExtract> path = List.of(folder("root"), folder(TRUSTED_FOLDER), artifact);

    TrustedByUtil.decorateWithTrustedBy(artifact, path, TRUST);

    assertEquals("BioPortal", artifact.getTrustedBy());
  }

  @Test
  void nearestParentFolderControlsTrustRatherThanAnyAncestor() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract(); artifact.setId("template");
    List<FolderServerResourceExtract> path = List.of(folder(TRUSTED_FOLDER), folder("ordinary-folder"), artifact);

    TrustedByUtil.decorateWithTrustedBy(artifact, path, TRUST);

    assertNull(artifact.getTrustedBy());
  }

  @Test
  void emptyPathSafelyLeavesArtifactUntrusted() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract();
    TrustedByUtil.decorateWithTrustedBy(artifact, List.of(), TRUST);
    assertNull(artifact.getTrustedBy());
  }

  @Test
  void nullPathSafelyLeavesArtifactUntrusted() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract();
    TrustedByUtil.decorateWithTrustedBy(artifact, (List<FolderServerResourceExtract>) null, TRUST);
    assertNull(artifact.getTrustedBy());
  }

  @Test
  void artifactOnlyPathSafelyLeavesArtifactUntrusted() {
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract(); artifact.setId("template");
    TrustedByUtil.decorateWithTrustedBy(artifact, List.of(artifact), TRUST);
    assertNull(artifact.getTrustedBy());
  }

  @Test
  void parentFolderOverloadUsesFolderIdentity() {
    FolderServerFolder parent = new FolderServerFolder(); parent.setId(TRUSTED_FOLDER);
    FolderServerTemplateExtract artifact = new FolderServerTemplateExtract();

    TrustedByUtil.decorateWithTrustedBy(artifact, parent, TRUST);

    assertEquals("BioPortal", artifact.getTrustedBy());
  }

  @Test
  void artifactReportUsesItsPathToProjectTrust() {
    FolderServerTemplateReport report = new FolderServerTemplateReport();
    report.setPathInfo(List.of(folder("root"), folder(TRUSTED_FOLDER)));

    TrustedByUtil.decorateWithTrustedBy(report, TRUST);

    assertEquals("BioPortal", report.getTrustedBy());
  }

  @Test
  void artifactReportWithNullPathSafelyRemainsUntrusted() {
    FolderServerTemplateReport report = new FolderServerTemplateReport();
    report.setPathInfo(null);

    TrustedByUtil.decorateWithTrustedBy(report, TRUST);

    assertNull(report.getTrustedBy());
  }

  private static FolderServerFolderExtract folder(String id) {
    FolderServerFolderExtract folder = new FolderServerFolderExtract();
    folder.setId(id);
    return folder;
  }
}
