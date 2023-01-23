package org.metadatacenter.server.neo4j.cypher.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.time.Instant;
import java.util.Map;

import static org.metadatacenter.model.ModelPaths.CREATED_BY;
import static org.metadatacenter.model.ModelPaths.LAST_UPDATED_BY;

public class CypherParamBuilderGroup extends AbstractCypherParamBuilder {

  public static CypherParameters createGroup(CedarGroupId groupURL, String name, String description, CedarUserId ownerId, String specialGroup) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    Long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, groupURL);
    params.put(NodeProperty.NAME, name);
    params.put(NodeProperty.DESCRIPTION, description);
    params.put(NodeProperty.CREATED_BY, ownerId);
    params.put(NodeProperty.CREATED_ON, nowString);
    params.put(NodeProperty.CREATED_ON_TS, nowTS);
    params.put(NodeProperty.LAST_UPDATED_BY, ownerId);
    params.put(NodeProperty.LAST_UPDATED_ON, nowString);
    params.put(NodeProperty.LAST_UPDATED_ON_TS, nowTS);
    params.put(NodeProperty.RESOURCE_TYPE, CedarResourceType.GROUP.getValue());
    params.put(NodeProperty.SPECIAL_GROUP, specialGroup);
    params.put(ParameterPlaceholder.USER_ID, ownerId);
    return params;
  }

  public static CypherParameters getGroupByName(String groupName) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.NAME, groupName);
    return params;
  }

  public static CypherParameters updateGroupById(CedarGroupId groupId, Map<NodeProperty, String> updateFields, CedarUserId updatedBy) {
    return updateResourceById(groupId, updateFields, updatedBy);
  }

  public static CypherParameters getGroupBySpecialValue(String specialGroupName) {
    CypherParameters params = new CypherParameters();
    params.put(ParameterPlaceholder.SPECIAL_GROUP, specialGroupName);
    return params;
  }

  public static CypherParameters matchId(CedarGroupId groupId) {
    return matchResourceByIdentity(groupId);
  }

  public static void tweakGroupProperties(JsonNode node, CypherParameters params) {
    params.put(NodeProperty.CREATED_BY, node.at(CREATED_BY).textValue());
    params.put(NodeProperty.LAST_UPDATED_BY, node.at(LAST_UPDATED_BY).textValue());
  }
}
