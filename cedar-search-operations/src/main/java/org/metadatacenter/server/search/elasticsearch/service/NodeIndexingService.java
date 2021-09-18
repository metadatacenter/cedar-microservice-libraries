package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.elasticsearch.client.Client;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.bridge.PathInfoBuilder;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.server.search.extraction.TemplateInstanceContentExtractor;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class NodeIndexingService extends AbstractIndexingService {

  private static final Logger log = LoggerFactory.getLogger(NodeIndexingService.class);

  private final CedarConfig cedarConfig;
  private final ElasticsearchIndexingWorker indexWorker;
  public final TemplateInstanceContentExtractor instanceContentExtractor;

  NodeIndexingService(CedarConfig cedarConfig, String indexName, Client client) {
    this.cedarConfig = cedarConfig;
    indexWorker = new ElasticsearchIndexingWorker(indexName, client);
    instanceContentExtractor = new TemplateInstanceContentExtractor(cedarConfig);
  }

  public IndexingDocumentDocument createIndexDocument(FileSystemResource node,
                                                      CedarNodeMaterializedPermissions permissions,
                                                      CedarNodeMaterializedCategories categories,
                                                      CedarRequestContext requestContext,
                                                      boolean isIndexRegenerationTask) throws CedarProcessingException {
    IndexingDocumentDocument ir = new IndexingDocumentDocument(node.getId());
    // Set node's path info
    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(requestContext);
    node.setPathInfo(PathInfoBuilder.getResourcePathExtract(requestContext, folderSession,
        CedarDataServices.getResourcePermissionServiceSession(requestContext), node));
    ir.setInfo(FolderServerNodeInfo.fromNode(node));
    ir.setMaterializedPermissions(permissions);
    ir.setMaterializedCategories(categories);
    ir.setSummaryText(getSummaryText(node));
    // Index field names and (when appropriate) their values
    if (node.getType().equals(CedarResourceType.INSTANCE) || node.getType().equals(CedarResourceType.TEMPLATE)
        || node.getType().equals(CedarResourceType.ELEMENT) || node.getType().equals(CedarResourceType.FIELD)) {
      ir.setInfoFields(instanceContentExtractor.generateInfoFields(node, requestContext, isIndexRegenerationTask));
    }
    return ir;
  }

  public IndexedDocumentId indexDocument(FileSystemResource node, CedarNodeMaterializedPermissions permissions,
                                         CedarNodeMaterializedCategories categories,
                                         CedarRequestContext requestContext) throws CedarProcessingException {
    return indexDocument(node, permissions, categories, requestContext, false);
  }

  public IndexedDocumentId indexDocument(FileSystemResource resource, CedarRequestContext requestContext) throws CedarProcessingException {
    log.debug("Indexing resource (id = " + resource.getId() + ")");
    ResourcePermissionServiceSession permissionSession =
        CedarDataServices.getResourcePermissionServiceSession(requestContext);
    CedarNodeMaterializedPermissions permissions =
        permissionSession.getResourceMaterializedPermission(resource.getResourceId());
    CategoryServiceSession categorySession = CedarDataServices.getCategoryServiceSession(requestContext);
    CedarNodeMaterializedCategories categories = new CedarNodeMaterializedCategories(resource.getId());
    if (resource.getType() != CedarResourceType.FOLDER) {
      categories = categorySession.getArtifactMaterializedCategories(CedarArtifactId.build(resource.getId(),
          resource.getType()));
    }
    return indexDocument(resource, permissions, categories, requestContext);
  }

  public IndexedDocumentId indexDocument(FileSystemResource resource, CedarNodeMaterializedPermissions permissions,
                                         CedarNodeMaterializedCategories categories, CedarRequestContext requestContext,
                                         boolean isIndexRegenerationTask) throws CedarProcessingException {
    log.debug("Indexing resource (id = " + resource.getId() + ")");
    IndexingDocumentDocument ir = createIndexDocument(resource, permissions, categories, requestContext,
        isIndexRegenerationTask);
    JsonNode jsonResource = JsonMapper.MAPPER.convertValue(ir, JsonNode.class);
    return indexWorker.addToIndex(jsonResource);
  }

  public void indexBatch(List<IndexingDocumentDocument> currentBatch) {
    indexWorker.addBatch(currentBatch);
  }

  private String getSummaryText(FileSystemResource node) {
    StringBuilder sb = new StringBuilder();
    if (node.getName() != null) {
      sb.append(node.getName());
    }
    if (node.getDescription() != null && !node.getDescription().isBlank()) {
      if (sb.length() > 0) {
        sb.append(" ");
      }
      sb.append(node.getDescription().trim());
    }
    if (node instanceof FolderServerArtifact) {
      if (node instanceof FolderServerSchemaArtifact) {
        FolderServerSchemaArtifact resource = (FolderServerSchemaArtifact) node;
        ResourceVersion version = resource.getVersion();
        if (version != null && version.getValue() != null && !version.getValue().isBlank()) {
          if (sb.length() > 0) {
            sb.append(" ");
          }
          sb.append(version.getValue().trim());
        }
      }

      FolderServerArtifact resource = (FolderServerArtifact) node;
      String identifier = resource.getIdentifier();
      if (identifier != null && !identifier.isBlank()) {
        if (sb.length() > 0) {
          sb.append(" ");
        }
        sb.append(identifier.trim());
      }
    }
    return sb.toString();
  }

  public long removeDocumentFromIndex(CedarFilesystemResourceId resourceId) throws CedarProcessingException {
    if (resourceId != null) {
      log.debug("Removing resource from index (id = " + resourceId + ")");
      return indexWorker.removeAllFromIndex(resourceId);
    } else {
      return -1;
    }
  }

  public long removeDocumentFromIndex(CedarFilesystemResourceId resourceId, boolean retry) throws CedarProcessingException {
    if (!retry) {
      return removeDocumentFromIndex(resourceId);
    }
    final int MAX_TRIES = 5;
    final int WAIT_MS = 1000;
    int currentTry = 1;
    long removedCount = 0;

    while (currentTry <= MAX_TRIES) {
      log.debug("Removing resource from index (id = " + resourceId + ")");
      removedCount = indexWorker.removeAllFromIndex(resourceId);
      if (removedCount > 0) {
        return removedCount;
      }
      else {
        log.debug("Could not remove resource from index (id = " + resourceId + ")");
        try {
          Thread.sleep(WAIT_MS);
        } catch (InterruptedException e) {
          log.error("Error while waiting before update execution", e);
        }
      }
    }
    return removedCount;
  }

}
