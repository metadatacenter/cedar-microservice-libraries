package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.Map;

public class CypherQueryBuilderFolder extends AbstractCypherQueryBuilder {

  public static String createRootFolder(FolderServerFolder newRoot) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        createFSFolder("root", newRoot) +
        " MERGE (user)-[:<REL.OWNS>]->(root)" +
        " RETURN root";
  }

  public static String getHomeFolderOf() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.IS_USER_HOME>:true, <PROP.HOME_OF>:{<PH.USER_ID>}})" +
        " RETURN folder";
  }

  public static String updateFolderById(Map<NodeProperty, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("folder", NodeProperty.LAST_UPDATED_BY));
    sb.append(buildSetter("folder", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("folder", NodeProperty.LAST_UPDATED_ON_TS));
    for (NodeProperty property : updateFields.keySet()) {
      sb.append(buildSetter("folder", property));
    }
    sb.append(" SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1");
    sb.append(" RETURN folder AS resource, folder._cedarRevision AS revision");
    return sb.toString();
  }

  public static String createFolderAsChildOfId(FolderServerFolder newFolder) {
    return createFSFolderAsChildOfId(newFolder);
  }

  public static String unlinkFolderFromParent() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER>)" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (parent)-[relation:<REL.CONTAINS>]->(folder)" +
        " DELETE relation" +
        " RETURN folder";
  }

  /**
   * Deletes one folder only when it is still empty. The no-op property write locks the folder
   * before the emptiness check, serializing this mutation with concurrent child relationship
   * creation. Returning the parent lets the caller distinguish deletion from a missing or
   * non-empty folder without a separate read.
   */
  public static String deleteEmptyFolderById() {
    return """
        MATCH (parent:<LABEL.FOLDER>)-[:<REL.CONTAINS>]->
              (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})
        SET folder.<PROP.ID> = folder.<PROP.ID>
        WITH parent, folder
        WHERE NOT EXISTS {
          MATCH (folder)-[:<REL.CONTAINS>]->()
        }
        DETACH DELETE folder
        RETURN parent
        """;
  }

  public static String getVersionedFolderById() {
    return " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})"
        + " RETURN folder AS resource, coalesce(folder._cedarRevision, 1) AS revision";
  }

  public static String lockFolderRevision() {
    return " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})"
        + " SET folder._cedarRevision = coalesce(folder._cedarRevision, 1)"
        + " RETURN folder._cedarRevision AS revision";
  }

  public static String getFolderLookupQueryById() {
    return "" +
        " MATCH (root:<LABEL.FOLDER> {<PROP.NAME>:{<PH.NAME>}})," +
        " (current:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>} })," +
        " path=shortestPath((root)-[:<REL.CONTAINS>*]->(current))" +
        " RETURN path";
  }

  public static String folderIsAncestorOf() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.PARENT_FOLDER_ID>}})" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.FOLDER_ID>}})" +
        " MATCH (parent)-[:<REL.CONTAINS>*0..]->(folder)" +
        " RETURN parent";
  }

  public static String linkFolderUnderFolder() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.PARENT_FOLDER_ID>}})" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.FOLDER_ID>}})" +
        " MERGE (parent)-[:<REL.CONTAINS>]->(folder)" +
        " SET folder.<PROP.PARENT_FOLDER_ID> = {<PH.PARENT_FOLDER_ID>}" +
        " RETURN folder";
  }

  /**
   * Reparents a folder in one statement. The root no-op write serializes all folder moves before
   * the cycle check; matching both parents before DELETE means a missing target leaves the old edge
   * intact.
   */
  public static String moveFolder() {
    return """
        MATCH (treeRoot:<LABEL.FOLDER> {<PROP.IS_ROOT>:true})
        SET treeRoot.<PROP.ID> = treeRoot.<PROP.ID>
        WITH treeRoot
        MATCH (oldParent:<LABEL.FOLDER>)-[oldRelation:<REL.CONTAINS>]->
              (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.FOLDER_ID>}})
        MATCH (newParent:<LABEL.FOLDER> {<PROP.ID>:{<PH.PARENT_FOLDER_ID>}})
        WHERE NOT EXISTS {
          MATCH (folder)-[:<REL.CONTAINS>*0..]->(newParent)
        }
        DELETE oldRelation
        MERGE (newParent)-[:<REL.CONTAINS>]->(folder)
        SET folder.<PROP.PARENT_FOLDER_ID> = {<PH.PARENT_FOLDER_ID>}
        SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1
        RETURN folder
        """;
  }

  public static String setFolderOwner() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.FOLDER_ID>}})" +
        " MERGE (user)-[:<REL.OWNS>]->(folder)" +
        " SET folder.<PROP.OWNED_BY> = {<PH.USER_ID>}" +
        " SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1" +
        " RETURN folder";
  }

  public static String removeFolderOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[relation:<REL.OWNS>]->(folder)" +
        " DELETE (relation)" +
        " SET folder.<PROP.OWNED_BY> = null" +
        " SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1" +
        " RETURN folder";
  }

  public static String getFolderLookupQueryByDepth(int cnt) {
    StringBuilder sb = new StringBuilder();
    if (cnt >= 1) {
      sb.append(" MATCH (f0:<LABEL.FOLDER> {<PROP.NAME>:$f0 })");
    }
    for (int i = 2; i <= cnt; i++) {
      String parentAlias = "f" + (i - 2);
      String childAlias = "f" + (i - 1);
      sb.append(" MATCH (");
      sb.append(childAlias);
      sb.append(":<LABEL.FOLDER> {<PROP.NAME>:$");
      sb.append(childAlias);
      sb.append("})");

      sb.append(" MATCH (");
      sb.append(parentAlias);
      sb.append(")-[:<REL.CONTAINS>]->(");
      sb.append(childAlias);
      sb.append(")");

    }
    sb.append(" RETURN *");
    return sb.toString();
  }

  public static String createFolderWithoutParent(FolderServerFolder newFolder) {
    return "" +
        createFSFolder(ALIAS_FOO, newFolder) +
        " RETURN " + ALIAS_FOO;
  }

  public static String getAllChildArtifacts() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>} })" +
        " MATCH (child:<LABEL.RESOURCE>)" +
        " MATCH (parent)-[:<REL.CONTAINS>]->(child)" +
        " RETURN child";
  }

  public static String getFolderById() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN folder";
  }

  public static String setOpen() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " SET folder.<PROP.IS_OPEN> = true" +
        " SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1" +
        " RETURN folder AS resource, folder._cedarRevision AS revision";
  }

  public static String setNotOpen() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " REMOVE folder.<PROP.IS_OPEN>" +
        " SET folder._cedarRevision = coalesce(folder._cedarRevision, 1) + 1" +
        " RETURN folder AS resource, folder._cedarRevision AS revision";
  }

  public static String getTotalCount() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER>)" +
        " RETURN count(folder)";
  }

  public static String getParentFolderById() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER>)-[:<REL.CONTAINS>]->(resource:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " RETURN folder";
  }
}
