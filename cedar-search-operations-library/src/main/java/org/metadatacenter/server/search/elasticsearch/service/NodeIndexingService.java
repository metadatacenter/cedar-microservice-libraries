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
import org.metadatacenter.server.search.util.IndexRebuildRegistry;
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

  /** Kept so that a write can be mirrored into an index this service was not built for. */
  private final String indexName;
  private final RestHighLevelClient client;
  private volatile String mirrorIndexName;
  private volatile ElasticsearchIndexingWorker mirrorWorker;

  NodeIndexingService(CedarConfig cedarConfig, String indexName, RestHighLevelClient client) {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(SystemComponent.SERVER_RESOURCE);

    nciCADSRValueSetsOntologyFilePath = environment.get(CedarEnvironmentVariable.CEDAR_CADSR_ONTOLOGIES_FOLDER.getName())
        + "/" + OntologyAndValueSetConstants.CADSR_VALUE_SETS_ONTOLOGY_FILE;

    indexWorker = new ElasticsearchIndexingWorker(indexName, client);
    instanceContentExtractor = new TemplateInstanceContentExtractor(cedarConfig);
    this.indexName = indexName;
    this.client = client;
  }

  /**
   * A writer for the index a rebuild is filling, when this service is not already that writer.
   *
   * <p>This service writes through the alias, which during a rebuild still names the old index. The
   * new index is filled under its own name, so without this every save made while a rebuild runs is
   * written only to the index that promotion then deletes.
   *
   * <p>Empty when no rebuild is running, and empty for the rebuild's own writer, which would
   * otherwise mirror into itself.
   */
  private Optional<ElasticsearchIndexingWorker> mirror() {
    String target = IndexRebuildRegistry.inProgressIndex().orElse(null);
    if (target == null || target.equals(indexName)) {
      return Optional.empty();
    }
    if (!target.equals(mirrorIndexName)) {
      mirrorWorker = new ElasticsearchIndexingWorker(target, client);
      mirrorIndexName = target;
    }
    return Optional.of(mirrorWorker);
  }

  /**
   * Mirror one write, and record that the rebuild must not overwrite it.
   *
   * <p>A failure here never fails the caller's own operation: the user's save has already succeeded
   * against the live index, and refusing it because a rebuild's copy could not be updated would make
   * a maintenance job able to break ordinary writing. It is logged at error with the identifier,
   * because the consequence — that one document is stale in the index about to be promoted — is
   * otherwise invisible and is repaired by re-indexing exactly that resource.
   */
  private void mirrorWrite(JsonNode document, String cedarId) {
    mirror().ifPresent(worker -> {
      try {
        worker.addToIndex(document, cedarId);
        IndexRebuildRegistry.recordLiveWrite(cedarId);
      } catch (Exception e) {
        log.error("The resource could not be mirrored into the index being rebuilt, so it will be stale"
            + " there once that index is promoted. Re-index it afterwards. Resource:" + cedarId, e);
      }
    });
  }

  /** The same for a removal, so a resource deleted during a rebuild does not come back on promotion. */
  private void mirrorRemoval(CedarFilesystemResourceId resourceId) {
    mirror().ifPresent(worker -> {
      try {
        worker.removeAllFromIndex(resourceId);
        IndexRebuildRegistry.recordLiveDelete(resourceId.getId());
      } catch (Exception e) {
        log.error("The resource could not be removed from the index being rebuilt, so it will reappear"
            + " in search once that index is promoted. Remove it afterwards. Resource:" + resourceId, e);
      }
    });
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
    // Set node's path info.
    //
    // The undecorated path: the only thing read back off it is the parent folder's identifier, in
    // FolderServerNodeInfo.fromNode. Asking for the decorated path instead ran getResourceAuthority
    // and userHasCapability against every element of every resource's ancestor chain — several graph
    // queries per element, for the whole repository — and then discarded every one of those answers.
    // They were also the wrong answers to store: they describe what the user running the rebuild may
    // do, in a document every user searches.
    FolderServiceSession folderSession = CedarDataServices.getInstance().getFolderServiceSession(requestContext);
    node.setPathInfo(PathInfoBuilder.getResourcePath(folderSession, node));
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
        CedarDataServices.getInstance().getResourcePermissionServiceSession(requestContext);
    CedarNodeMaterializedPermissions permissions =
        permissionSession.getResourceMaterializedPermission(resource.getResourceId());
    CategoryServiceSession categorySession = CedarDataServices.getInstance().getCategoryServiceSession(requestContext);
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
    // A caller that could not find the resource has nothing to index. Say so, rather than
    // dereferencing null several frames deeper and reporting it as a NullPointerException.
    if (resource == null) {
      throw new CedarProcessingException("Unable to index a resource that does not exist");
    }
    log.debug("Indexing resource (id = " + resource.getId() + ")");
    IndexingDocumentDocument ir = createIndexDocument(resource, permissions, categories, requestContext,
        isIndexRegenerationTask);
    JsonNode jsonResource = JsonMapper.STRICT_MAPPER.convertValue(ir, JsonNode.class);
    // Index under the CEDAR id, so re-indexing replaces the resource's document in place
    IndexedDocumentId indexed = indexWorker.addToIndex(jsonResource, resource.getId());
    mirrorWrite(jsonResource, resource.getId());
    return indexed;
  }

  public void indexBatch(List<IndexingDocumentDocument> currentBatch) throws CedarProcessingException {
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
      long removed = indexWorker.removeAllFromIndex(resourceId);
      mirrorRemoval(resourceId);
      return removed;
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
