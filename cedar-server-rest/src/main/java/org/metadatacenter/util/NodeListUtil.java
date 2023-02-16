package org.metadatacenter.util;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.model.request.NodeListQueryType;
import org.metadatacenter.model.request.NodeListRequest;
import org.metadatacenter.model.response.FolderServerNodeListResponse;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.util.http.LinkHeaderUtil;
import org.metadatacenter.util.http.PagedSortedTypedQuery;

import java.util.List;

public abstract class NodeListUtil {

  public static NodeListRequest buildNodeListRequest(PagedSortedTypedQuery pagedSortedTypedQuery) {
    int limit = pagedSortedTypedQuery.getLimit();
    int offset = pagedSortedTypedQuery.getOffset();
    List<String> sortList = pagedSortedTypedQuery.getSortList();
    List<CedarResourceType> resourceTypeList = pagedSortedTypedQuery.getResourceTypeList();
    ResourceVersionFilter version = pagedSortedTypedQuery.getVersion();
    ResourcePublicationStatusFilter publicationStatus = pagedSortedTypedQuery.getPublicationStatus();

    NodeListRequest req = new NodeListRequest();
    req.setResourceTypes(resourceTypeList);
    req.setVersion(version);
    req.setPublicationStatus(publicationStatus);
    req.setLimit(limit);
    req.setOffset(offset);
    req.setSort(sortList);

    return req;
  }

  public static FolderServerNodeListResponse findFolderContents(CedarConfig cedarConfig, FolderServiceSession folderSession, CedarFolderId folderId, String absoluteUrl,
                                                          List<FolderServerResourceExtract> pathInfo, PagedSortedTypedQuery pagedSortedTypedQuery) {
    FolderServerNodeListResponse r = new FolderServerNodeListResponse();
    r.setNodeListQueryType(NodeListQueryType.FOLDER_CONTENT);

    NodeListRequest req = buildNodeListRequest(pagedSortedTypedQuery);

    r.setRequest(req);

    List<FolderServerResourceExtract> resources = folderSession.findFolderContentsExtract(folderId, req);

    for (FolderServerResourceExtract resourceExtract : resources) {
      TrustedByUtil.decorateWithTrustedby(resourceExtract, pathInfo, cedarConfig.getTrustedFolders().getFoldersMap());
    }

    long total = folderSession.findFolderContentsCount(folderId, req);

    r.setTotalCount(total);
    r.setCurrentOffset(req.getOffset());

    r.setResources(resources);

    r.setPathInfo(pathInfo);

    r.setPaging(LinkHeaderUtil.getPagingLinkHeaders(absoluteUrl, total, req.getLimit(), req.getOffset()));

    return r;
  }


}
