package org.metadatacenter.server.search.util;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.InclusionSubgraphServiceSession;
import org.metadatacenter.server.neo4j.cypher.sort.QuerySortOptions;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class RegenerateInclusionSubgraphTask {

  private static final Logger log = LoggerFactory.getLogger(RegenerateInclusionSubgraphTask.class);

  private final static int BATCH_SIZE = 100;

  private final CedarConfig cedarConfig;


  public RegenerateInclusionSubgraphTask(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  /**
   * What a completed regeneration did: how many artifacts had their arcs rewritten, and which ones
   * were left as they were because the artifact server would not serve them.
   */
  public record Outcome(long updated, List<String> unreadableArtifacts) {
    public Outcome {
      unreadableArtifacts = List.copyOf(unreadableArtifacts);
    }
  }

  public Outcome regenerateInclusionSubgraph(CedarRequestContext cedarAdminRequestContext) throws CedarProcessingException {

    FolderServiceSession folderSession = CedarDataServices.getInstance().getFolderServiceSession(cedarAdminRequestContext);
    InclusionSubgraphServiceSession inclusionSubgraphSession = CedarDataServices.getInstance().getInclusionSubgraphServiceSession(cedarAdminRequestContext);

    List<CedarResourceType> resourceTypeList = new ArrayList<>(List.of(CedarResourceType.TEMPLATE, CedarResourceType.ELEMENT));
    ResourceVersionFilter version = ResourceVersionFilter.ALL;
    ResourcePublicationStatusFilter publicationStatus = ResourcePublicationStatusFilter.ALL;
    List<String> sortList = new ArrayList<>(List.of(QuerySortOptions.DEFAULT_SORT_FIELD.getName()));

    long total = folderSession.viewAllCount(resourceTypeList, version, publicationStatus);
    log.warn("INCLUSION-SUBGRAPH Total count:" + total);
    int limit = BATCH_SIZE;
    int offset = 0;
    int retrievedCount = 0;
    long updated = 0;
    List<String> unreadable = new ArrayList<>();
    do {
      List<FolderServerResourceExtract> folderServerResourceExtracts = folderSession.viewAll(resourceTypeList, version, publicationStatus, limit, offset, sortList);
      for (FolderServerResourceExtract resource : folderServerResourceExtracts) {
        if (InclusionSubgraphUtil.updateResourceInclusionInfo(cedarAdminRequestContext, cedarConfig, resource, inclusionSubgraphSession)) {
          updated++;
        } else {
          unreadable.add(resource.getId());
        }
      }
      offset += limit;
      retrievedCount = folderServerResourceExtracts.size();
      log.warn("INCLUSION-SUBGRAPH Offset:" + offset + ", retrieved count:" + retrievedCount);
    } while (retrievedCount > 0);
    if (!unreadable.isEmpty()) {
      log.warn("INCLUSION-SUBGRAPH {} artifacts could not be read and keep their previous arcs: {}", unreadable.size(),
          unreadable);
    }
    return new Outcome(updated, unreadable);
  }

}
