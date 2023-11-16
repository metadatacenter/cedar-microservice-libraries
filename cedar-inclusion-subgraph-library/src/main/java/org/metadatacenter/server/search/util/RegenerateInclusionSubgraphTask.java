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
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
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

  public void regenerateInclusionSubgraph(CedarRequestContext cedarAdminRequestContext) throws CedarProcessingException {

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(cedarAdminRequestContext);
    InclusionSubgraphServiceSession inclusionSubgraphSession = CedarDataServices.getInclusionSubgraphServiceSession(cedarAdminRequestContext);

    List<CedarResourceType> resourceTypeList = new ArrayList<>(List.of(CedarResourceType.TEMPLATE, CedarResourceType.ELEMENT));
    ResourceVersionFilter version = ResourceVersionFilter.ALL;
    ResourcePublicationStatusFilter publicationStatus = ResourcePublicationStatusFilter.ALL;
    List<String> sortList = new ArrayList<>(List.of(Neo4JUtil.escapePropertyName(QuerySortOptions.DEFAULT_SORT_FIELD.getFieldName())));

    long total = folderSession.viewAllCount(resourceTypeList, version, publicationStatus);
    log.warn("INCLUSION-SUBGRAPH Total count:" + total);
    int limit = BATCH_SIZE;
    int offset = 0;
    int retrievedCount = 0;
    do {
      List<FolderServerResourceExtract> folderServerResourceExtracts = folderSession.viewAll(resourceTypeList, version, publicationStatus, limit, offset, sortList);
      for (FolderServerResourceExtract resource : folderServerResourceExtracts) {
        InclusionSubgraphUtil.updateResourceInclusionInfo(cedarAdminRequestContext, cedarConfig, resource, inclusionSubgraphSession);
      }
      offset += limit;
      retrievedCount = folderServerResourceExtracts.size();
      log.warn("INCLUSION-SUBGRAPH Offset:" + offset + ", retrieved count:" + retrievedCount);
      System.out.println();
    } while (retrievedCount > 0);
  }

}
