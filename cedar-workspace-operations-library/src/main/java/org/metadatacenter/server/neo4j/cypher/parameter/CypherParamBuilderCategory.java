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
