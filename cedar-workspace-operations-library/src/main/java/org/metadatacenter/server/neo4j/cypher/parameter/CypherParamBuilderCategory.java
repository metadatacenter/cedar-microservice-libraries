package org.metadatacenter.server.neo4j.cypher.parameter;

import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarCategoryId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.time.Instant;
import java.util.Map;
import java.util.List;

public class CypherParamBuilderCategory extends AbstractCypherParamBuilder {

  public static CypherParameters createCategory(CedarCategoryId parentCategoryId, CedarCategoryId newCategoryId, String categoryName, String categoryDescription, String categoryIdentifier,
                                                CedarUserId userId) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    // BaseDataGroup
    params.put(NodeProperty.ID, newCategoryId);
    params.put(NodeProperty.RESOURCE_TYPE, CedarResourceType.CATEGORY.getValue());
    params.put(NodeProperty.CREATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    // TimestampDataGroup
    params.put(NodeProperty.CREATED_ON_TS, nowTS);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);
    // NameDescriptionIdentifierGroup
    params.put(NodeProperty.NAME, categoryName);
    params.put(NodeProperty.NAME_LOWER, categoryName.toLowerCase());
    params.put(NodeProperty.DESCRIPTION, categoryDescription);
    params.put(NodeProperty.IDENTIFIER, categoryIdentifier);
    // UsersDataGroup
    params.put(NodeProperty.CREATED_BY, userId);
    params.put(NodeProperty.LAST_UPDATED_BY, userId);
    params.put(NodeProperty.OWNED_BY, userId);
    //
    params.put(NodeProperty.PARENT_CATEGORY_ID, parentCategoryId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters getCategoryByParentAndName(CedarCategoryId parentId, String name) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.NAME, name);
    params.put(ParameterPlaceholder.PARENT_CATEGORY_ID, parentId);
    return params;
  }

  public static CypherParameters getAllCategories(int limit, int offset) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.LIMIT, limit);
    params.put(ParameterPlaceholder.OFFSET, offset);
    return params;
  }

  public static CypherParameters matchId(CedarCategoryId categoryId) {
    return matchResourceByIdentity(categoryId);
  }

  public static CypherParameters matchIdentifier(String identifier) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.IDENTIFIER, identifier);
    return params;
  }

  public static CypherParameters matchCategoryAndUser(CedarCategoryId categoryId, CedarUserId userId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.CATEGORY_ID, categoryId);
    params.put(ParameterPlaceholder.USER_ID, userId);
    return params;
  }

  public static CypherParameters matchCategory(CedarCategoryId categoryId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.CATEGORY_ID, categoryId);
    return params;
  }

  public static CypherParameters replacePermissions(CedarCategoryId categoryId, CedarUserId ownerId,
                                                    List<String> userIds, List<String> attachUserIds,
                                                    List<String> writeUserIds, List<String> groupIds,
                                                    List<String> attachGroupIds, List<String> writeGroupIds,
                                                    long currentRevision) {
    CypherParameters params = matchCategory(categoryId);
    params.put(ParameterPlaceholder.OWNER_ID, ownerId);
    params.put(ParameterPlaceholder.USER_ID_LIST, userIds);
    params.put(ParameterPlaceholder.ATTACH_USER_ID_LIST, attachUserIds);
    params.put(ParameterPlaceholder.WRITE_USER_ID_LIST, writeUserIds);
    params.put(ParameterPlaceholder.GROUP_ID_LIST, groupIds);
    params.put(ParameterPlaceholder.ATTACH_GROUP_ID_LIST, attachGroupIds);
    params.put(ParameterPlaceholder.WRITE_GROUP_ID_LIST, writeGroupIds);
    params.put(ParameterPlaceholder.CURRENT_REVISION, currentRevision);
    return params;
  }

  public static CypherParameters categoryIdAndArtifactId(CedarCategoryId categoryId, CedarArtifactId artifactId) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.CATEGORY_ID, categoryId);
    params.put(ParameterPlaceholder.ARTIFACT_ID, artifactId);
    return params;
  }

  public static CypherParameters updateCategoryById(CedarCategoryId categoryId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    return updateResourceById(categoryId, updateFields, updatedBy);
  }

  public static CypherParameters getCategoryById(CedarCategoryId categoryId) {
    return matchResourceByIdentity(categoryId);
  }
}
