package org.metadatacenter.server.neo4j.cypher.parameter;

import org.apache.commons.lang.StringUtils;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.neo4j.PathUtil;
import org.metadatacenter.server.neo4j.cypher.CypherQueryParameter;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.util.Map;

public class CypherParamBuilderFolder extends AbstractCypherParamBuilder {

  public static CypherParameters matchFolderIdAndParentFolderId(CedarFolderId folderId, CedarFolderId parentFolderId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.FOLDER_ID, folderId);
    params.put(ParameterPlaceholder.PARENT_FOLDER_ID, parentFolderId);
    return params;
  }

  public static CypherParameters updateFolderById(CedarFolderId folderId, Map<? extends CypherQueryParameter, String> updateFields,
                                                  CedarUserId updatedBy) {
    return updateResourceById(folderId, updateFields, updatedBy);
  }

  public static CypherParameters getFolderLookupByDepthParameters(PathUtil pathUtil, String path) {
    String normalizedPath = pathUtil.normalizePath(path);
    String[] parts = StringUtils.split(normalizedPath, pathUtil.getSeparator());
    CypherParameters folderNames = new CypherParameters();
    folderNames.put(getFolderAlias(0), pathUtil.getRootPath());
    for (int i = 0; i < parts.length; i++) {
      folderNames.put(getFolderAlias(i + 1), parts[i]);
    }
    return folderNames;
  }

  public static CypherParameters getFolderLookupByIdParameters(String pathName, CedarFolderId folderId) {
    return getResourceByIdentityAndName(folderId, pathName);
  }

  public static CypherParameters createFolder(LinkedDataUtil linkedDataUtil, FolderServerFolder newFolder, CedarFolderId cedarFolderId) {
    String newFolderId = linkedDataUtil.buildNewLinkedDataId(CedarResourceType.FOLDER);
    newFolder.setId(newFolderId);
    return createFilesystemResource(newFolder, cedarFolderId);
  }

  public static CypherParameters matchId(CedarFolderId folderId) {
    return matchResourceByIdentity(folderId);
  }
}
