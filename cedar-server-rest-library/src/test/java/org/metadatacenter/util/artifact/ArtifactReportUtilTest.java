package org.metadatacenter.util.artifact;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.*;
import org.metadatacenter.model.folderserver.extract.*;
import org.metadatacenter.model.folderserver.report.*;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ArtifactReportUtilTest {

  private FolderServiceSession folderSession;
  private ResourcePermissionServiceSession permissionSession;
  private CategoryServiceSession categorySession;
  private CedarRequestContext context;
  private CedarConfig cedarConfig;

  @BeforeEach
  void setUp() {
    folderSession = mock(FolderServiceSession.class);
    permissionSession = mock(ResourcePermissionServiceSession.class);
    categorySession = mock(CategoryServiceSession.class);
    context = mock(CedarRequestContext.class);
    cedarConfig = mock(CedarConfig.class);
  }

  static Stream<Arguments> artifactTypes() {
    return Stream.of(
        Arguments.of((Supplier<FolderServerArtifact>) FolderServerField::new, FolderServerFieldReport.class),
        Arguments.of((Supplier<FolderServerArtifact>) FolderServerElement::new, FolderServerElementReport.class),
        Arguments.of((Supplier<FolderServerArtifact>) FolderServerTemplate::new, FolderServerTemplateReport.class),
        Arguments.of((Supplier<FolderServerArtifact>) FolderServerInstance::new, FolderServerInstanceReport.class)
    );
  }

  static Stream<Supplier<FolderServerArtifact>> schemaArtifactTypes() {
    return Stream.of(FolderServerField::new, FolderServerElement::new, FolderServerTemplate::new);
  }

  @ParameterizedTest
  @MethodSource("artifactTypes")
  void dispatchesEveryArtifactTypeToItsConcreteReport(Supplier<FolderServerArtifact> artifactFactory,
                                                       Class<? extends FolderServerArtifactReport> reportType) {
    FolderServerArtifact artifact = artifactFactory.get();
    artifact.setId("artifact-1");
    artifact.setName("A real artifact");
    stubEmptyDecorations();

    FolderServerArtifactReport report = report(artifact);

    assertInstanceOf(reportType, report);
    assertEquals("artifact-1", report.getId());
    assertEquals("A real artifact", report.getName());
    assertEquals(artifact.getType(), report.getType());
  }

  @ParameterizedTest
  @MethodSource("schemaArtifactTypes")
  void schemaArtifactsLoadBothCompleteAndReadableVersionHistories(Supplier<FolderServerArtifact> artifactFactory) {
    FolderServerArtifact artifact = artifactFactory.get();
    artifact.setId("schema-1");
    stubEmptyDecorations();

    FolderServerArtifactReport report = report(artifact);

    FolderServerSchemaArtifactReport schemaReport = (FolderServerSchemaArtifactReport) report;
    verify(folderSession).getVersionHistory(schemaReport.getResourceId());
    verify(folderSession).getVersionHistoryWithPermission(schemaReport.getResourceId());
  }

  @Test
  void instanceDoesNotLoadSchemaHistoryOrTemplateCount() {
    FolderServerInstance instance = instance("instance-1");
    stubEmptyDecorations();

    report(instance);

    verify(folderSession, never()).getVersionHistory(any());
    verify(folderSession, never()).getVersionHistoryWithPermission(any());
    verify(folderSession, never()).getNumberOfInstances(any());
  }

  @Test
  void templateIncludesItsInstanceCount() {
    FolderServerTemplate template = template("template-1");
    stubEmptyDecorations();
    when(folderSession.getNumberOfInstances(CedarTemplateId.build("template-1"))).thenReturn(37L);

    FolderServerTemplateReport report = (FolderServerTemplateReport) report(template);

    assertEquals(37L, report.getNumberOfInstances());
    verify(folderSession).getNumberOfInstances(CedarTemplateId.build("template-1"));
  }

  @Test
  void preservesVersionOrderAndRedactsOnlyUnreadableVersions() {
    FolderServerTemplate template = template("template-current");
    FolderServerTemplateExtract v1 = templateExtract("v1", "first");
    FolderServerTemplateExtract v2 = templateExtract("v2", "secret second");
    FolderServerTemplateExtract v3 = templateExtract("v3", "third");
    when(folderSession.getVersionHistory(any())).thenReturn(List.of(v1, v2, v3));
    when(folderSession.getVersionHistoryWithPermission(any())).thenReturn(List.of(v3, v1));
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    List<FolderServerArtifactExtract> versions = report(template).getVersions();

    assertEquals(List.of("v1", "v2", "v3"), versions.stream().map(FolderServerArtifactExtract::getId).toList());
    assertSame(v1, versions.get(0));
    assertFalse(versions.get(1).isActiveUserCanRead());
    assertNull(versions.get(1).getName());
    assertSame(v3, versions.get(2));
  }

  @Test
  void versionReadabilityIsMatchedByIdRatherThanObjectIdentity() {
    FolderServerTemplate template = template("template-current");
    FolderServerTemplateExtract complete = templateExtract("v1", "complete metadata");
    FolderServerTemplateExtract permissionProjection = templateExtract("v1", "different object");
    when(folderSession.getVersionHistory(any())).thenReturn(List.of(complete));
    when(folderSession.getVersionHistoryWithPermission(any())).thenReturn(List.of(permissionProjection));
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    List<FolderServerArtifactExtract> versions = report(template).getVersions();

    assertSame(complete, versions.get(0));
    assertEquals("complete metadata", versions.get(0).getName());
  }

  @Test
  void readableIsBasedOnRelationshipIncludesTheFullTemplateExtract() {
    FolderServerInstance instance = instance("instance-1");
    instance.setIsBasedOn(CedarTemplateId.build("template-1"));
    FolderServerTemplateExtract template = templateExtract("template-1", "Visible template");
    when(folderSession.findResourceExtractById(instance.getIsBasedOn())).thenReturn(template);
    when(permissionSession.userHasReadAccessToResource(template.getResourceId())).thenReturn(true);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerInstanceReport report = (FolderServerInstanceReport) report(instance);

    assertSame(template, report.getIsBasedOnExtract());
  }

  @Test
  void unreadableIsBasedOnRelationshipKeepsIdentityButHidesMetadata() {
    FolderServerInstance instance = instance("instance-1");
    instance.setIsBasedOn(CedarTemplateId.build("template-1"));
    FolderServerTemplateExtract template = templateExtract("template-1", "Secret template");
    when(folderSession.findResourceExtractById(instance.getIsBasedOn())).thenReturn(template);
    when(permissionSession.userHasReadAccessToResource(template.getResourceId())).thenReturn(false);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerTemplateExtract visible = ((FolderServerInstanceReport) report(instance)).getIsBasedOnExtract();

    assertEquals("template-1", visible.getId());
    assertFalse(visible.isActiveUserCanRead());
    assertNull(visible.getName());
  }

  @Test
  void absentIsBasedOnDoesNotQueryForAnExtractOrPermission() {
    FolderServerInstance instance = instance("instance-1");
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerInstanceReport report = (FolderServerInstanceReport) report(instance);

    assertNull(report.getIsBasedOnExtract());
    verify(folderSession, never()).findResourceExtractById(any(CedarArtifactId.class));
    verify(permissionSession, never()).userHasReadAccessToResource(any());
  }

  @Test
  void missingIsBasedOnExtractDoesNotPerformPermissionLookup() {
    FolderServerInstance instance = instance("instance-1");
    instance.setIsBasedOn(CedarTemplateId.build("missing-template"));
    when(folderSession.findResourceExtractById(instance.getIsBasedOn())).thenReturn(null);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerInstanceReport report = (FolderServerInstanceReport) report(instance);

    assertNull(report.getIsBasedOnExtract());
    verify(permissionSession, never()).userHasReadAccessToResource(any());
  }

  @Test
  void readableDerivedFromRelationshipIncludesTheFullArtifactExtract() {
    FolderServerField artifact = field("field-2");
    artifact.setDerivedFrom(CedarUntypedArtifactId.build("field-1"));
    FolderServerFieldExtract source = fieldExtract("field-1", "Visible source");
    stubEmptyHistory();
    when(folderSession.findResourceExtractById(artifact.getDerivedFrom())).thenReturn(source);
    when(permissionSession.userHasReadAccessToResource(source.getResourceId())).thenReturn(true);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerArtifactReport report = report(artifact);

    assertSame(source, report.getDerivedFromExtract());
  }

  @Test
  void unreadableDerivedFromRelationshipKeepsIdentityAndTypeButHidesMetadata() {
    FolderServerElement artifact = element("element-2");
    artifact.setDerivedFrom(CedarUntypedArtifactId.build("element-1"));
    FolderServerElementExtract source = elementExtract("element-1", "Secret source");
    stubEmptyHistory();
    when(folderSession.findResourceExtractById(artifact.getDerivedFrom())).thenReturn(source);
    when(permissionSession.userHasReadAccessToResource(source.getResourceId())).thenReturn(false);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerArtifactExtract visible = report(artifact).getDerivedFromExtract();

    assertEquals("element-1", visible.getId());
    assertEquals(CedarResourceType.ELEMENT, visible.getType());
    assertFalse(visible.isActiveUserCanRead());
    assertNull(visible.getName());
  }

  @Test
  void absentDerivedFromDoesNotAddASecondRelationshipLookup() {
    FolderServerInstance instance = instance("instance-1");
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerArtifactReport report = report(instance);

    assertNull(report.getDerivedFromExtract());
    verify(folderSession, never()).findResourceExtractById(any(CedarArtifactId.class));
  }

  @Test
  void missingDerivedFromExtractDoesNotPerformPermissionLookup() {
    FolderServerField artifact = field("field-2");
    artifact.setDerivedFrom(CedarUntypedArtifactId.build("missing-field"));
    stubEmptyHistory();
    when(folderSession.findResourceExtractById(artifact.getDerivedFrom())).thenReturn(null);
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());

    FolderServerArtifactReport report = report(artifact);

    assertNull(report.getDerivedFromExtract());
    verify(permissionSession, never()).userHasReadAccessToResource(any());
  }

  @Test
  void attachesCategoryPathsWithoutFlatteningTheirHierarchy() {
    FolderServerInstance instance = instance("instance-1");
    FolderServerCategoryExtract root = category("root", "Root");
    FolderServerCategoryExtract child = category("child", "Child");
    FolderServerCategoryExtract other = category("other", "Other");
    List<List<FolderServerCategoryExtract>> paths = List.of(List.of(root, child), List.of(other));
    CedarArtifactId instanceId = (CedarArtifactId) instance.getResourceId();
    when(categorySession.getAttachedCategoryPaths(instanceId)).thenReturn(paths);

    FolderServerArtifactReport report = report(instance);

    assertSame(paths, report.getCategories());
    verify(categorySession).getAttachedCategoryPaths(instanceId);
  }

  @Test
  void delegatesPermissionProjectionWithTheExactContextConfigSessionAndNewReport() {
    FolderServerInstance instance = instance("instance-1");
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());
    AtomicReference<FolderServerArtifactReport> decorated = new AtomicReference<>();

    FolderServerArtifactReport report = ArtifactReportUtil.getArtifactReport(
        context, cedarConfig, instance, folderSession, permissionSession, categorySession,
        (actualContext, actualSession, actualConfig, actualReport) -> {
          assertSame(context, actualContext);
          assertSame(permissionSession, actualSession);
          assertSame(cedarConfig, actualConfig);
          decorated.set(actualReport);
        });

    assertSame(report, decorated.get());
  }

  private FolderServerArtifactReport report(FolderServerArtifact artifact) {
    return ArtifactReportUtil.getArtifactReport(context, cedarConfig, artifact, folderSession, permissionSession,
        categorySession, (ignoredContext, ignoredSession, ignoredConfig, ignoredReport) -> { });
  }

  private void stubEmptyDecorations() {
    stubEmptyHistory();
    when(categorySession.getAttachedCategoryPaths(any())).thenReturn(List.of());
  }

  private void stubEmptyHistory() {
    when(folderSession.getVersionHistory(any())).thenReturn(List.of());
    when(folderSession.getVersionHistoryWithPermission(any())).thenReturn(List.of());
  }

  private static FolderServerTemplate template(String id) {
    FolderServerTemplate artifact = new FolderServerTemplate(); artifact.setId(id); return artifact;
  }

  private static FolderServerField field(String id) {
    FolderServerField artifact = new FolderServerField(); artifact.setId(id); return artifact;
  }

  private static FolderServerElement element(String id) {
    FolderServerElement artifact = new FolderServerElement(); artifact.setId(id); return artifact;
  }

  private static FolderServerInstance instance(String id) {
    FolderServerInstance artifact = new FolderServerInstance(); artifact.setId(id); return artifact;
  }

  private static FolderServerTemplateExtract templateExtract(String id, String name) {
    FolderServerTemplateExtract extract = new FolderServerTemplateExtract(); extract.setId(id); extract.setName(name); return extract;
  }

  private static FolderServerFieldExtract fieldExtract(String id, String name) {
    FolderServerFieldExtract extract = new FolderServerFieldExtract(); extract.setId(id); extract.setName(name); return extract;
  }

  private static FolderServerElementExtract elementExtract(String id, String name) {
    FolderServerElementExtract extract = new FolderServerElementExtract(); extract.setId(id); extract.setName(name); return extract;
  }

  private static FolderServerCategoryExtract category(String id, String name) {
    FolderServerCategoryExtract extract = new FolderServerCategoryExtract(); extract.setId(id); extract.setName(name); return extract;
  }
}
