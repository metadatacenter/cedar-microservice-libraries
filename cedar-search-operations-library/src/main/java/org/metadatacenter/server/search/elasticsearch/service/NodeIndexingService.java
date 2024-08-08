package org.metadatacenter.server.search.elasticsearch.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.bridge.PathInfoBuilder;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.constant.OntologyAndValueSetConstants;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFilesystemResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.info.FolderServerNodeInfo;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.search.PossibleValues;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.IndexedDocumentId;
import org.metadatacenter.server.search.elasticsearch.worker.ElasticsearchIndexingWorker;
import org.metadatacenter.server.search.extraction.TemplateInstanceContentExtractor;
import org.metadatacenter.server.search.extraction.ValueSetsExtractor;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.metadatacenter.util.json.JsonMapper;
import org.opensearch.client.RestHighLevelClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


public class NodeIndexingService extends AbstractIndexingService {

  private static final Logger log = LoggerFactory.getLogger(NodeIndexingService.class);

  private final ElasticsearchIndexingWorker indexWorker;
  public final TemplateInstanceContentExtractor instanceContentExtractor;
  private final String nciCADSRValueSetsOntologyFilePath;

  NodeIndexingService(CedarConfig cedarConfig, String indexName, RestHighLevelClient client) {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE);

    nciCADSRValueSetsOntologyFilePath = environment.get(CedarEnvironmentVariable.CEDAR_CADSR_ONTOLOGIES_FOLDER.getName())
        + "/" + OntologyAndValueSetConstants.CADSR_VALUE_SETS_ONTOLOGY_FILE;

    indexWorker = new ElasticsearchIndexingWorker(indexName, client);
    instanceContentExtractor = new TemplateInstanceContentExtractor(cedarConfig);
  }

  public void readValueSets() throws CedarProcessingException {
    if (nciCADSRValueSetsOntologyFilePath != null && !nciCADSRValueSetsOntologyFilePath.isEmpty()) {
      ValueSetsExtractor.getInstance().loadValueSetsOntology(nciCADSRValueSetsOntologyFilePath);
    } else {
      throw new CedarProcessingException("No path configured for value set ontology");
    }
  }

  public IndexingDocumentDocument createIndexDocument(FileSystemResource node, CedarNodeMaterializedPermissions permissions,
                                                      CedarNodeMaterializedCategories categories, CedarRequestContext requestContext,
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

    if (node.getType().equals(CedarResourceType.FIELD)) {
      List<String> valueSetURIs = instanceContentExtractor.generateValueSetsURIs(node, requestContext);
      Set<String> valueLabels = new HashSet<>();
      Set<String> valueConcepts = new HashSet<>();

      for (String valueSetURI : valueSetURIs) {
        Set<String> valueSetValueURIs = ValueSetsExtractor.getInstance().getSubClassURIs(valueSetURI);

        for (String valueSetValueURI : valueSetValueURIs) {
          // Extract value labels (both prefLabel and notation) and add them to the set
          Optional<String> valueLabel = ValueSetsExtractor.getInstance().getAnnotation(valueSetValueURI, ValueSetsExtractor.Annotation.LABEL);
          if (valueLabel.isPresent()) {
            valueLabels.add(valueLabel.get());
          }
          Optional<String> valueNotation = ValueSetsExtractor.getInstance().getAnnotation(valueSetValueURI, ValueSetsExtractor.Annotation.NOTATION);
          if (valueNotation.isPresent()) {
            if (!valueLabel.isPresent() || (valueLabel.isPresent() && !valueLabel.get().equalsIgnoreCase(valueNotation.get()))) {
              valueLabels.add(valueNotation.get());
            }
          }

          Optional<String> valueConcept = ValueSetsExtractor.getInstance().getAnnotation(valueSetValueURI, ValueSetsExtractor.Annotation.RELATED_MATCH);
          if (valueConcept.isPresent()) { // We store only the fragment of the value concept URI
            String valueConceptURI = valueConcept.get();
            String[] components = valueConceptURI.split("#", 2);
            if (components.length == 2) {
              String namespace = components[0];
              String fragment = components[1];
              if (!fragment.isEmpty()) {
                valueConcepts.add(fragment);
              }
            }
          }
        }
      }
      if (!valueLabels.isEmpty() || !valueConcepts.isEmpty()) {
        ir.setPossibleValues(new PossibleValues(valueLabels, valueConcepts));
      }
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
      if (!sb.isEmpty()) {
        sb.append(" ");
      }
      sb.append(node.getDescription().trim());
    }
    if (node instanceof FolderServerArtifact) {
      if (node instanceof FolderServerSchemaArtifact) {
        FolderServerSchemaArtifact resource = (FolderServerSchemaArtifact) node;
        ResourceVersion version = resource.getVersion();
        if (version != null && version.getValue() != null && !version.getValue().isBlank()) {
          if (!sb.isEmpty()) {
            sb.append(" ");
          }
          sb.append(version.getValue().trim());
        }
      }

      FolderServerArtifact resource = (FolderServerArtifact) node;
      String identifier = resource.getIdentifier();
      if (identifier != null && !identifier.isBlank()) {
        if (!sb.isEmpty()) {
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
    final int MAX_TRIES = 10;
    final int WAIT_MS = 300;
    int currentTry = 1;
    long removedCount = 0;

    while (currentTry <= MAX_TRIES) {
      log.debug("Removing resource from index (id = " + resourceId + ")");
      removedCount = 0;
      try {
        removedCount = indexWorker.removeAllFromIndex(resourceId);
      } catch (CedarProcessingException e) {
        // DO nothing, we will retry
      }
      if (removedCount > 0) {
        return removedCount;
      } else {
        log.debug("Could not remove resource from index (id = " + resourceId + ")");
        try {
          Thread.sleep(WAIT_MS);
        } catch (InterruptedException e) {
          log.error("Error while waiting before update execution", e);
        }
      }
      currentTry++;
    }
    return removedCount;
  }

}
