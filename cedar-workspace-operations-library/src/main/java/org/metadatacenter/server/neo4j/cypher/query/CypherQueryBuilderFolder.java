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
    sb.append(" RETURN folder");
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

  public static String deleteFolderContentsRecursivelyById() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (folder)-[relation:<REL.CONTAINS>*0..]->(child)" +
        " DETACH DELETE child" +
        " DETACH DELETE folder";
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
        " RETURN folder";
  }

  public static String setFolderOwner() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.FOLDER_ID>}})" +
        " MERGE (user)-[:<REL.OWNS>]->(folder)" +
        " SET folder.<PROP.OWNED_BY> = {<PH.USER_ID>}" +
        " RETURN folder";
  }

  public static String removeFolderOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[relation:<REL.OWNS>]->(folder)" +
        " DELETE (relation)" +
        " SET folder.<PROP.OWNED_BY> = null" +
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
        " RETURN folder";
  }

  public static String setNotOpen() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER> {<PROP.ID>:{<PH.ID>}})" +
        " REMOVE folder.<PROP.IS_OPEN>" +
        " RETURN folder";
  }

  public static String getTotalCount() {
    return "" +
        " MATCH (folder:<LABEL.FOLDER>)" +
        " RETURN count(folder)";
  }
}
