package org.metadatacenter.server.search.elasticsearch.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.server.security.model.auth.NodeSharePermission;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.client.RequestOptions;
import org.opensearch.client.RestHighLevelClient;
import org.opensearch.client.core.CountRequest;
import org.opensearch.client.core.CountResponse;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NodePermissionProjectionTest {
  @Test
  void projectionUpdatesOnlyGraphFieldsAndExplicitlyClearsAbsentValues() throws Exception {
    var document = JsonMapper.STRICT_MAPPER.readValue("""
        {"cid":"resource-1", "summaryText":"name", "infoFields":[{"fieldName":"retained content"}],
         "possibleValues":{"valueLabels":["retained value"]}, "computedEverybodyPermission":null}
        """, IndexingDocumentDocument.class);
    var patch = NodeIndexingService.permissionProjectionFields(document);
    assertFalse(patch.has("infoFields"), "permission work must not overwrite indexed artifact content");
    assertFalse(patch.has("possibleValues"));
    assertTrue(patch.has("computedEverybodyPermission"));
    assertTrue(patch.get("computedEverybodyPermission").isNull(), "revocation must clear a previous value");
    assertEquals("name", patch.get("summaryText").asText());
    assertEquals(7, patch.size());
  }

  /**
   * A free-text search matches the summary text. A schema artifact is found by its name, description,
   * version and identifier. An instance is found by the same parts except a version, and a folder by its
   * name and description. Blank parts are left out, so no doubled space appears.
   */
  @ParameterizedTest
  @MethodSource("summaries")
  void theSummaryTextJoinsWhatAResourceIsSearchedBy(FileSystemResource resource, String expected) throws Exception {
    var config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
    when(config.getServers().getArtifact().getBase()).thenReturn("http://127.0.0.1:1");
    var client = mock(RestHighLevelClient.class);
    when(client.count(any(CountRequest.class), any(RequestOptions.class))).thenReturn(mock(CountResponse.class));
    var services = mock(CedarDataServices.class);
    when(services.getFolderServiceSession(any())).thenReturn(mock(FolderServiceSession.class));
    var service = new NodeIndexingService(config, "live", client);

    try (var dataServices = mockStatic(CedarDataServices.class)) {
      dataServices.when(CedarDataServices::getInstance).thenReturn(services);
      var projection = service.preparePermissionProjection(resource,
          new CedarNodeMaterializedPermissions(resource.getResourceId(), NodeSharePermission.NONE), null, null);
      assertEquals(expected, projection.patch().get("summaryText").asText());
    }
  }

  static Stream<Arguments> summaries() {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId("https://repo.metadatacenter.org/templates/11111111-1111-1111-1111-111111111111");
    template.setName("Blood pressure");
    template.setDescription("  Systolic and diastolic  ");
    template.setVersion("1.2.0");
    template.setIdentifier(" doi:10.5555/bp ");

    FolderServerTemplate sparse = new FolderServerTemplate();
    sparse.setId("https://repo.metadatacenter.org/templates/22222222-2222-2222-2222-222222222222");
    sparse.setName("Visit");
    sparse.setDescription("   ");
    sparse.setVersion("0.0.1");
    sparse.setIdentifier("");

    FolderServerInstance instance = new FolderServerInstance();
    instance.setId("https://repo.metadatacenter.org/template-instances/33333333-3333-3333-3333-333333333333");
    instance.setDescription("Measured at rest");
    instance.setIdentifier("ID-7");

    FolderServerFolder folder = new FolderServerFolder();
    folder.setId("https://repo.metadatacenter.org/folders/44444444-4444-4444-4444-444444444444");
    folder.setName("Shared");
    folder.setDescription("Team folder");

    return Stream.of(
        Arguments.of(template, "Blood pressure Systolic and diastolic 1.2.0 doi:10.5555/bp"),
        Arguments.of(sparse, "Visit 0.0.1"),
        Arguments.of(instance, "Measured at rest ID-7"),
        Arguments.of(folder, "Shared Team folder"));
  }
}
