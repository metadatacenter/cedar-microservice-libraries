package org.metadatacenter.server.search.util;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.search.IndexingDocumentDocument;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedCategories;
import org.metadatacenter.server.security.model.auth.CedarNodeMaterializedPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.metadatacenter.constant.ElasticsearchConstants.DOCUMENT_CEDAR_ID;

public class RegenerateSearchIndexTask {

  private static final Logger log = LoggerFactory.getLogger(RegenerateSearchIndexTask.class);

  private final static int BATCH_SIZE = 1000;

  private final CedarConfig cedarConfig;


  public RegenerateSearchIndexTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  public void ensureSearchIndexExists() throws CedarProcessingException {
    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getSearchIndex().getName();

    indexUtils.ensureIndexAndAliasExist(esManagementService, aliasName);
  }

  public void regenerateSearchIndex(boolean force, CedarRequestContext requestContext) throws CedarProcessingException {
    regenerateSearchIndex(force, requestContext, new IndexingProgress());
  }

  /**
   * The same rebuild, reporting how far it has got.
   *
   * <p>The progress it records is what this task already computed and discarded: it logged a
   * percentage every hundred resources and kept nothing, so the only account of an eight-hour job was
   * in the resource server's log. The caller passes in the record so that whatever started the
   * rebuild — a status route, a monitoring page — can read it while the rebuild is still running.
   */
  public void regenerateSearchIndex(boolean force, CedarRequestContext requestContext, IndexingProgress progress)
      throws CedarProcessingException {
    log.info("Regenerating search index. Force:" + force);

    IndexUtils indexUtils = new IndexUtils(cedarConfig);
    ElasticsearchManagementService esManagementService = indexUtils.getEsManagementService();
    NodeSearchingService nodeSearchingService = indexUtils.getNodeSearchingService();

    String aliasName = cedarConfig.getElasticsearchConfig().getIndexes().getSearchIndex().getName();
    NodeIndexingService nodeIndexingService = null;
    // Declared out here so the finally block can stop the mirroring it started, on every path out.
    String newIndexName = null;

    boolean regenerate = true;
    try {
      ResourcePermissionServiceSession permissionSession = CedarDataServices.getInstance().getResourcePermissionServiceSession(requestContext);
      CategoryServiceSession categorySession = CedarDataServices.getInstance().getCategoryServiceSession(requestContext);
      // Get all resources
      log.info("Reading all resources from the existing search index.");
      progress.enterPhase(IndexingPhase.ENUMERATING);
      List<FileSystemResource> resources = indexUtils.findAllResources(requestContext, progress);
      // Checks if is necessary to regenerate the index or not
      if (!force) {
        progress.enterPhase(IndexingPhase.COMPARING);
        log.info("Force is false. Checking if it is necessary to regenerate the search index from Neo4j.");
        // Check if the index exists (using the alias). If it exists, check if it contains all resources
        if (esManagementService.indexExists(aliasName)) {
          log.warn("The search index/alias '" + aliasName + "' is present!");
          // Use the artifact ids to check if the resources in the DBs and in the index are different
          List<String> dbResourceIds = getResourceIds(resources);
          log.info("No. of nodes in Neo4j that are expected to be indexed: " + dbResourceIds.size());
          List<String> indexResourceIds = nodeSearchingService.findAllValuesForField(DOCUMENT_CEDAR_ID);
          log.info("No. of content document in the index: " + indexResourceIds.size());
          if (dbResourceIds.size() == indexResourceIds.size()) {
            // Compare the two lists
            List<String> tmp1 = new ArrayList(dbResourceIds);
            List<String> tmp2 = new ArrayList(indexResourceIds);
            Collections.sort(tmp1);
            Collections.sort(tmp2);
            if (tmp1.equals(tmp2)) {
              regenerate = false;
              log.info("Neo4j and search index match. It is not necessary to regenerate the index");
            } else {
              log.warn("Neo4j and search index do not match! (different ids)");
            }
          } else {
            log.warn("Neo4j and search index do not match! (different size)");
          }
        } else {
          log.warn("The search index/alias '" + aliasName + "' does not exist!");
        }
      } else {
        log.info("Force is true. It is not needed to compare the search index and Neo4j");
      }
      if (regenerate) {
        log.info("After all the checks were performed, it seems that the index needs to be regenerated!");
        // Create new index and set it up
        newIndexName = indexUtils.getNewIndexName(aliasName);
        esManagementService.createSearchIndex(newIndexName);
        log.info("Search index created:" + newIndexName);
        // From here on, every live save and delete is mirrored into this index as well as into the
        // one the alias still names. Until this existed, a rebuild discarded every write made while
        // it ran, because promotion deletes the index those writes went to.
        IndexRebuildRegistry.begin(newIndexName);

        nodeIndexingService = indexUtils.getNodeIndexingService(newIndexName);

        progress.enterPhase(IndexingPhase.INDEXING);
        progress.setTotal(resources.size());
        progress.setTotalByType(countByType(resources));

        // Get resources content and index it
        int count = 1;
        int batchCount = 1;
        int skipped = 0;
        // Refreshed every batch rather than asked per resource: against a shared store that would be
        // a round trip for every resource in the repository, for an answer that is almost always no.
        Set<String> liveTouched = IndexRebuildRegistry.liveTouched();
        List<IndexingDocumentDocument> currentBatch = new ArrayList<>();
        for (FileSystemResource node : resources) {
          if (liveTouched.contains(node.getId())) {
            // A live write has already put a current version of this resource into the new index, or
            // removed it. This work list was read before that happened, so writing from it now would
            // replace the newer document with the one this rebuild started with.
            skipped++;
            progress.advance(node.getType());
            count++;
            continue;
          }
          try {
            CedarNodeMaterializedPermissions perm = permissionSession.getResourceMaterializedPermission(node.getResourceId());
            CedarNodeMaterializedCategories categories = null;
            if (node instanceof FolderServerArtifact) {
              categories = categorySession.getArtifactMaterializedCategories((CedarArtifactId) node.getResourceId());
            }
            currentBatch.add(nodeIndexingService.createIndexDocument(node, perm, categories, requestContext, true));
          } catch (Exception e) {
            throw new CedarProcessingException("Error while building index document: " + node.getId(), e);
          }
          progress.advance(node.getType());
          if (count % 100 == 0) {
            float percent = (float) (100 * count) / resources.size();
            log.info(String.format("Progress: %.0f%%", percent));
          }
          if (currentBatch.size() >= BATCH_SIZE) {
            log.info(String.format("Batch progress: %d", batchCount));
            liveTouched = IndexRebuildRegistry.liveTouched();
            nodeIndexingService.indexBatch(currentBatch);
            currentBatch.clear();
            batchCount++;
          }
          count++;
        }

        log.info(String.format("Batch progress remaining: %d", currentBatch.size()));
        if (currentBatch.size() > 0) {
          nodeIndexingService.indexBatch(currentBatch);
        }

        // Make all bulk-indexed documents searchable, verify that the rebuild is complete, atomically
        // promote it, and only then remove the old concrete indices.
        if (skipped > 0) {
          log.info(skipped + " resources were left to the version a live write had already put in the"
              + " new index");
        }
        // What should be in the new index is the work list, plus what was created while the rebuild
        // ran, minus what was deleted while it ran.
        Set<String> snapshotIds = new HashSet<>(getResourceIds(resources));
        long expectedDocumentCount = resources.size() + IndexRebuildRegistry.expectedCountAdjustment(snapshotIds);
        indexUtils.verifyAndPromoteIndex(esManagementService, aliasName, newIndexName, expectedDocumentCount,
            progress);
      } else {
        log.info(
            "After all the checks were performed, it seems that the index does not need to be regenerated this time.");
      }
    } catch (Exception e) {
      log.error("Error while regenerating index", e);
      throw new CedarProcessingException(e);
    } finally {
      // However this ended, live writes must stop being mirrored into an index that is now either
      // promoted or abandoned.
      IndexRebuildRegistry.end(newIndexName);
      progress.enterPhase(IndexingPhase.DONE);
      // Clear template nodes cache
      if (nodeIndexingService != null) {
        nodeIndexingService.instanceContentExtractor.clearNodesCache();
      }
    }
  }

  /**
   * The work list broken down by type. Reported alongside the total because it is the one breakdown
   * a reader can check against what the repository is known to hold, and because the types differ so
   * much in cost that knowing the mix explains the rate.
   */
  private Map<CedarResourceType, Long> countByType(List<FileSystemResource> resources) {
    Map<CedarResourceType, Long> counts = new EnumMap<>(CedarResourceType.class);
    for (FileSystemResource resource : resources) {
      counts.merge(resource.getType(), 1L, Long::sum);
    }
    return counts;
  }

  private List<String> getResourceIds(List<FileSystemResource> resources) {
    List<String> ids = new ArrayList<>();
    for (FileSystemResource resource : resources) {
      ids.add(resource.getId());
    }
    return ids;
  }

}
