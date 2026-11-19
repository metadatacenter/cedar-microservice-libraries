package org.metadatacenter.server.search.util;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.core.CedarConstants;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The regeneration that rewrites the inclusion arcs of every template and element, run against an
 * embedded graph and an artifact server that serves one element and not another.
 *
 * <p>The worker reports the task's outcome, and the worker's own tests supply that outcome ready-made,
 * so only a run of the task shows that an artifact the server will not serve is reported as unreadable
 * and keeps its arcs, while a served one has its arcs rewritten from its body.
 */
@Tag("integration")
class RegenerateInclusionSubgraphTaskTest {

  private static final Map<String, String> SERVED = new ConcurrentHashMap<>();

  private static HttpServer artifactServer;
  private static CedarConfig cedarConfig;
  private static CedarRequestContext adminContext;

  @BeforeAll
  static void startGraphAndArtifactServer() throws Exception {
    artifactServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    artifactServer.createContext("/", exchange -> {
      String path = URLDecoder.decode(exchange.getRequestURI().getRawPath(), StandardCharsets.UTF_8);
      String body = SERVED.entrySet().stream()
          .filter(served -> path.contains(served.getKey().substring(served.getKey().lastIndexOf('/') + 1)))
          .map(Map.Entry::getValue).findFirst().orElse(null);
      byte[] bytes = (body == null ? "{\"statusCode\":404,\"status\":\"ERROR\"}" : body).getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().add("Content-Type", "application/json");
      exchange.sendResponseHeaders(body == null ? 404 : 200, bytes.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(bytes);
      }
    });
    artifactServer.start();

    EmbeddedCedarNeo4j.startAndRedirectEnvironment(Map.of(
        "CEDAR_REDIS_PERSISTENT_PORT", "1",
        "CEDAR_ARTIFACT_SERVER_HOST", "127.0.0.1",
        "CEDAR_ARTIFACT_HTTP_PORT", String.valueOf(artifactServer.getAddress().getPort())));
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.SERVER_WORKER);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_WORKER));
    adminContext = CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig));
  }

  @AfterAll
  static void stopArtifactServer() {
    artifactServer.stop(0);
  }

  @Test
  void servedArtifactsGetTheirArcsAndAnUnservedOneIsReportedAndKeepsItsOwn() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(adminContext);
    InclusionSubgraphServiceSession inclusion = CedarDataServices.getInstance().getInclusionSubgraphServiceSession(adminContext);
    CedarFolderId home = CedarDataServices.getInstance()
        .getFolderServiceSession(CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig)))
        .findHomeFolderOf().getResourceId();
    FolderServerArtifact servedElement = create(folders, new FolderServerElement(), CedarResourceType.ELEMENT,
        "Served element", home);
    FolderServerArtifact unservedElement = create(folders, new FolderServerElement(), CedarResourceType.ELEMENT,
        "Unserved element", home);
    FolderServerArtifact template = create(folders, new FolderServerTemplate(), CedarResourceType.TEMPLATE,
        "Template including the served element", home);
    // An arc the unserved element held before; the regeneration must leave it, not clear it.
    inclusion.updateInclusionArcs(unservedElement.getResourceId(), List.of(servedElement.getId()));
    SERVED.put(servedElement.getId(), artifact(servedElement.getId()).toString());
    ObjectNode templateBody = artifact(template.getId());
    templateBody.withObject("properties").set("element", JsonMapper.MAPPER.createObjectNode()
        .put("type", "object").put("@id", servedElement.getId()).put("@type", CedarConstants.TEMPLATE_ELEMENT_TYPE_URI));
    SERVED.put(template.getId(), templateBody.toString());

    RegenerateInclusionSubgraphTask.Outcome outcome =
        new RegenerateInclusionSubgraphTask(cedarConfig).regenerateInclusionSubgraph(adminContext);

    assertEquals(2, outcome.updated(), "the served template and element are rewritten");
    assertEquals(List.of(unservedElement.getId()), outcome.unreadableArtifacts());
    assertEquals(List.of(template.getId()), ids(inclusion.listIncludingTemplates(servedElement.getResourceId())),
        "the served template's arc is written from its body");
    assertEquals(List.of(unservedElement.getId()), ids(inclusion.listIncludingElements(servedElement.getResourceId())),
        "the unserved element keeps the arc it had");
  }

  private static FolderServerArtifact create(FolderServiceSession folders, FolderServerSchemaArtifact artifact,
                                             CedarResourceType type, String name, CedarFolderId parent) {
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(type));
    artifact.setName(name);
    artifact.setDescription("Created by RegenerateInclusionSubgraphTaskTest");
    artifact.setVersion("0.0.1");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    return folders.createResourceAsChildOfId(artifact, parent);
  }

  private static ObjectNode artifact(String id) {
    ObjectNode artifact = JsonMapper.MAPPER.createObjectNode().put("@id", id);
    artifact.putObject("properties");
    return artifact;
  }

  private static List<String> ids(List<? extends FileSystemResource> resources) {
    return resources.stream().map(FileSystemResource::getId).toList();
  }
}
