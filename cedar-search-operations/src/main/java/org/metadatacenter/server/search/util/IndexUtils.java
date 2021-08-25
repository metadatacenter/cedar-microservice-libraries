package org.metadatacenter.server.search.util;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchManagementService;
import org.metadatacenter.server.search.elasticsearch.service.ElasticsearchServiceFactory;
import org.metadatacenter.server.search.elasticsearch.service.NodeIndexingService;
import org.metadatacenter.server.search.elasticsearch.service.NodeSearchingService;
import org.metadatacenter.server.url.MicroserviceUrlUtil;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedSortedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IndexUtils {

  protected static final Logger log = LoggerFactory.getLogger(IndexUtils.class);

  private final int limit;
  private final int maxAttempts;
  private final int delayAttempts;
  private final MicroserviceUrlUtil microserviceUrlUtil;
  private final CedarConfig cedarConfig;

  public IndexUtils(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    this.microserviceUrlUtil = cedarConfig.getMicroserviceUrlUtil();
    this.limit = cedarConfig.getResourceRESTAPI().getPagination().getMaxPageSize();
    this.maxAttempts = cedarConfig.getSearchSettings().getSearchRetrieveSettings().getMaxAttempts();
    this.delayAttempts = cedarConfig.getSearchSettings().getSearchRetrieveSettings().getDelayAttempts();
  }

  /**
   * This method retrieves all the resources from the Neo4j Server that are expected to be in the search index.
   * Those resources that don't have to be in the index, such as the "/" folder and the "Lost+Found" folder are ignored.
   */
  public List<FileSystemResource> findAllResources(CedarRequestContext context) {
    log.info("Retrieving all resources.");
    List<FileSystemResource> resources = new ArrayList<>();
    boolean finished = false;

    int offset = 0;
    int countSoFar = 0;
    while (!finished) {
      log.info("Reading resources");

      FolderServerNodeListResponse pagedNodes = null;
      try {
        pagedNodes = findAllNodes(context, Optional.empty(), limit, offset);
      } catch (CedarException e) {
        log.error("Error while reading nodes", e);
        e.printStackTrace();
      }
      int count = 0;
      long totalCount = 0;
      long currentOffset = 0;
      if (pagedNodes != null) {
        count = pagedNodes.getResources().size();
        totalCount = pagedNodes.getTotalCount();
        countSoFar += count;
        log.info("Retrieved " + countSoFar + "/" + totalCount + " resources");
        currentOffset = pagedNodes.getCurrentOffset();
        for (FolderServerResourceExtract folderServerNodeExtract : pagedNodes.getResources()) {
          FileSystemResource folderServerNode = FileSystemResource.fromNodeExtract(folderServerNodeExtract);
          if (needsIndexing(folderServerNode)) {
            resources.add(folderServerNode);
          } else {
            log.info("The resource '" + folderServerNode.getName() + "' has been ignored");
          }
        }
      }
      if (currentOffset + count >= totalCount) {
        finished = true;
      } else {
        offset = offset + count;
      }
    }
    return resources;
  }

  public boolean needsIndexing(FileSystemResource folderServerNode) {
    boolean needsIndexing = true;
    if (folderServerNode.getType() == CedarResourceType.FOLDER) {
      FolderServerFolder folderServerFolder = (FolderServerFolder) folderServerNode;
      if (folderServerFolder.isSystem() || folderServerFolder.isUserHome()) {
        needsIndexing = false;
      }
    }
    return needsIndexing;
  }

  public String getNewIndexName(String prefix) {
    Instant now = Instant.now();
    String dateTimeFormatterString = "uuuu-MM-dd't'HH:mm:ss";
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(dateTimeFormatterString).withZone(ZoneId
        .systemDefault());
    String nowString = dateTimeFormatter.format(now);
    return prefix + "-" + nowString;
  }

  public void deleteOldIndices(ElasticsearchManagementService esManagementService, String aliasName,
                               String newIndexName) throws CedarProcessingException {
    log.info("Listing existing indices.");
    List<String> indexNames = esManagementService.getAllIndices();
    log.info("Found " + indexNames.size());
    for (String iName : indexNames) {
      log.info("Found index:" + iName);
      if (iName.startsWith(aliasName)) {
        if (!iName.equals(newIndexName)) {
          log.info("Deleting existing index:" + iName);
          esManagementService.deleteIndex(iName);
        } else {
          log.info("Keeping existing index, since it was just generated:" + iName);
        }
      } else {
        log.info("Not touching existing index, it is not in the scope of this task:" + iName);
      }
    }
  }

  public void ensureIndexAndAliasExist(ElasticsearchManagementService esManagementService, String aliasName)
      throws CedarProcessingException {
    int cedarIndexCount = 0;

    log.info("Looking for existing CEDAR indices...");
    List<String> indexNames = esManagementService.getAllIndices();
    log.info("Found total of " + indexNames.size() + " indices");
    for (String iName : indexNames) {
      log.info("Looking at index:" + iName);
      if (iName.startsWith(aliasName)) {
        log.info("Found CEDAR index:" + iName);
        cedarIndexCount++;
      }
    }
    log.info("Found total of " + cedarIndexCount + " CEDAR indices");
    if (cedarIndexCount > 0) {
      log.info("Nothing to do!");
    } else {
      String newIndexName = getNewIndexName(aliasName);
      log.info("Creating brand new CEDAR index:" + newIndexName);
      esManagementService.createSearchIndex(newIndexName);
      esManagementService.addAlias(newIndexName, aliasName);
    }

  }

  public ElasticsearchManagementService getEsManagementService() {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);
    return esServiceFactory.getManagementService();
  }

  public NodeSearchingService getNodeSearchingService() {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);
    return esServiceFactory.nodeSearchingService();
  }

  public NodeIndexingService getNodeIndexingService(String newIndexName) {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);
    return esServiceFactory.nodeIndexingService(newIndexName);
  }

  public NodeIndexingService getNodeIndexingService() {
    ElasticsearchServiceFactory esServiceFactory = ElasticsearchServiceFactory.getInstance(cedarConfig);
    return esServiceFactory.nodeIndexingService();
  }

  public FolderServerNodeListResponse findAllNodes(CedarRequestContext c, Optional<String> sortParam, int limit, int offset) throws CedarException {

    PagedSortedQuery pagedSortedQuery = new PagedSortedQuery(
        cedarConfig.getResourceRESTAPI().getPagination())
        .sort(sortParam)
        .limit(Optional.of(limit))
        .offset(Optional.of(offset));
    pagedSortedQuery.validate();

    List<String> sortList = pagedSortedQuery.getSortList();

    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(c);

    // Retrieve all resources
    List<FolderServerResourceExtract> resources = folderSession.findAllNodes(limit, offset, sortList);

    // Build response
    FolderServerNodeListResponse r = new FolderServerNodeListResponse();
    r.setNodeListQueryType(NodeListQueryType.ALL_NODES);
    NodeListRequest req = new NodeListRequest();
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);
    r.setRequest(req);
    long total = folderSession.findAllNodesCount();
    r.setTotalCount(total);
    r.setCurrentOffset(offset);
    r.setResources(resources);
    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders("", total, limit, offset));

    return r;
  }

  public void loadValueSetsOntology() throws CedarProcessingException
  {
    getNodeIndexingService().readValueSets();
  }

}
