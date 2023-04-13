package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.Map;

public class CypherQueryBuilderArtifact extends AbstractCypherQueryBuilder {

  public static String createResourceAsChildOfId(FolderServerArtifact newResource) {
    return createFSResourceAsChildOfId(newResource);
  }

  public static String updateResourceById(Map<NodeProperty, String> updateFields) {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})");
    sb.append(buildSetter("artifact", NodeProperty.LAST_UPDATED_BY));
    sb.append(buildSetter("artifact", NodeProperty.LAST_UPDATED_ON));
    sb.append(buildSetter("artifact", NodeProperty.LAST_UPDATED_ON_TS));
    for (NodeProperty property : updateFields.keySet()) {
      sb.append(buildSetter("artifact", property));
    }
    sb.append(" RETURN artifact");
    return sb.toString();
  }

  public static String deleteArtifactById() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " DETACH DELETE artifact";
  }

  public static String unlinkArtifactFromParentFolder() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER>)" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (parent)-[relation:<REL.CONTAINS>]->(artifact)" +
        " DELETE relation" +
        " RETURN artifact";
  }

  public static String linkArtifactUnderFolder() {
    return "" +
        " MATCH (parent:<LABEL.FOLDER> {<PROP.ID>:{<PH.PARENT_FOLDER_ID>}})" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ARTIFACT_ID>}})" +
        " MERGE (parent)-[:<REL.CONTAINS>]->(artifact)" +
        " RETURN artifact";
  }

  public static String setArtifactOwner() {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ARTIFACT_ID>}})" +
        " MERGE (user)-[:<REL.OWNS>]->(artifact)" +
        " SET artifact.<PROP.OWNED_BY> = {<PH.USER_ID>}" +
        " RETURN artifact";
  }

  public static String removeResourceOwner() {
    return "" +
        " MATCH (user:<LABEL.USER>)" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " MATCH (user)-[relation:<REL.OWNS>]->(artifact)" +
        " DELETE relation" +
        " SET artifact.<PROP.OWNED_BY> = null" +
        " RETURN artifact";
  }

  public static String getResourceLookupQueryById() {
    return "" +
        " MATCH (root:<LABEL.FOLDER> {<PROP.NAME>:{<PH.NAME>}})," +
        " (current:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})," +
        " path=shortestPath((root)-[:<REL.CONTAINS>*]->(current))" +
        " RETURN path";
  }

  public static String createResourceWithoutParent(FolderServerArtifact newResource) {
    return "" +
        createFSResource(ALIAS_FOO, newResource) +
        " RETURN " + ALIAS_FOO;
  }

  public static String setDerivedFrom() {
    return "" +
        " MATCH (nr:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.SOURCE_ID>}})" +
        " MATCH (or:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.TARGET_ID>}})" +
        " MERGE (nr)-[:<REL.DERIVEDFROM>]->(or)" +
        " SET nr.<PROP.DERIVED_FROM> = {<PH.TARGET_ID>}" +
        " RETURN nr";
  }

  public static String unsetLatestVersion() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_LATEST_VERSION> = false" +
        " RETURN artifact";
  }

  public static String setLatestVersion() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_LATEST_VERSION> = true" +
        " RETURN artifact";
  }

  public static String unsetLatestDraftVersion() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_LATEST_DRAFT_VERSION> = false" +
        " RETURN artifact";
  }

  public static String setLatestPublishedVersion() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_LATEST_PUBLISHED_VERSION> = true" +
        " RETURN artifact";
  }

  public static String unsetLatestPublishedVersion() {
    return "" +
        " MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_LATEST_PUBLISHED_VERSION> = false" +
        " RETURN artifact";
  }

  public static String getIsBasedOnCount() {
    return "" +
        " MATCH (instance:<LABEL.INSTANCE> {<PROP.IS_BASED_ON>:{<PH.ID>}}) " +
        " RETURN COUNT(instance)";
  }

  public static String getVersionHistory() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ID>}})");
    sb.append(" MATCH p=(resnew:<LABEL.ARTIFACT>)-[:<REL.PREVIOUSVERSION>*0..]->");
    sb.append("(artifact)-[:<REL.PREVIOUSVERSION>*0..]->(resold:<LABEL.ARTIFACT>)");
    sb.append(" RETURN p ORDER BY length(p) DESC LIMIT 1");
    return sb.toString();
  }

  public static String getVersionHistoryWithPermission() {
    StringBuilder sb = new StringBuilder();
    sb.append(" MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})");
    sb.append(" MATCH (artifact:<LABEL.ARTIFACT> {<PROP.ID>:{<PH.ARTIFACT_ID>}})");
    sb.append(" MATCH p=(resnew:<LABEL.ARTIFACT>)-[:<REL.PREVIOUSVERSION>*0..]->");
    sb.append("(artifact)-[:<REL.PREVIOUSVERSION>*0..]->(resold:<LABEL.ARTIFACT>)");
    sb.append(" WITH nodes(p) as ns, user");
    sb.append(" ORDER BY length(p) DESC LIMIT 1");
    sb.append(" UNWIND ns AS artifact");
    sb.append(" MATCH (artifact:Artifact)");
    sb.append(getResourcePermissionConditions(" WHERE ", "artifact"));
    sb.append(" RETURN artifact");
    return sb.toString();
  }

  public static String setOpen() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " SET artifact.<PROP.IS_OPEN> = true" +
        " RETURN artifact";
  }

  public static String setNotOpen() {
    return "" +
        " MATCH (artifact:<LABEL.RESOURCE> {<PROP.ID>:{<PH.ID>}})" +
        " REMOVE artifact.<PROP.IS_OPEN>" +
        " RETURN artifact";
  }

}
