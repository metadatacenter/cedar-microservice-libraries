package org.metadatacenter.server.neo4j.cypher.parameter;

import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.metadatacenter.model.ModelPaths.OSLC_MODIFIED_BY;
import static org.metadatacenter.model.ModelPaths.PAV_CREATED_BY;
import static org.metadatacenter.model.ModelPaths.SCHEMA_NAME;

public class CypherParamBuilderGroup extends AbstractCypherParamBuilder {

  public static CypherParameters createGroup(CedarGroupId groupURL, String name, String description, CedarUserId ownerId, String specialGroup) {
    Instant now = Instant.now();
    String nowString = CedarConstants.xsdDateTimeFormatter.format(now);
    long nowTS = now.getEpochSecond();
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.ID, groupURL);
    params.put(NodeProperty.NAME, name);
    params.put(NodeProperty.NAME_LOWER, name.toLowerCase(Locale.ROOT));
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
    // Lowercased to match the NAME_LOWER property the query looks at. A null name matches no node,
    // which is the right answer to "is this name taken".
    params.put(ParameterPlaceholder.NAME, groupName == null ? null : groupName.toLowerCase(Locale.ROOT));
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

  public static CypherParameters replaceGroupUsers(CedarGroupId groupId, List<String> userIds,
                                                    List<String> administratorIds, List<String> memberIds,
                                                    long currentRevision) {
    CypherParameters params = matchId(groupId);
    params.put(ParameterPlaceholder.USER_ID_LIST, userIds);
    params.put(ParameterPlaceholder.ADMINISTRATOR_ID_LIST, administratorIds);
    params.put(ParameterPlaceholder.MEMBER_ID_LIST, memberIds);
    params.put(ParameterPlaceholder.CURRENT_REVISION, currentRevision);
    return params;
  }

  /**
   * Completes the parameters of a group read back from an export. The export holds the group's creator and
   * modifier under their JSON-LD names, and its name but not the lower-cased copy that name lookups match, which
   * createGroup's query requires.
   */
  public static void tweakGroupProperties(JsonNode node, CypherParameters params) {
    params.put(NodeProperty.CREATED_BY, node.at(PAV_CREATED_BY).textValue());
    params.put(NodeProperty.LAST_UPDATED_BY, node.at(OSLC_MODIFIED_BY).textValue());
    String name = node.at(SCHEMA_NAME).textValue();
    params.put(NodeProperty.NAME_LOWER, name == null ? null : name.toLowerCase(Locale.ROOT));
  }
}
