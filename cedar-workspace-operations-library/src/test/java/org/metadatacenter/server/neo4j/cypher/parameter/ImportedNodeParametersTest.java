package org.metadatacenter.server.neo4j.cypher.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.metadatacenter.server.neo4j.parameter.ParameterPlaceholder;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The admin tool's import recreates groups and filesystem nodes from their exported JSON. Each create query needs
 * every property a created node has, in the form the graph stores, so the exported values are converted and the
 * properties an export does not hold are supplied.
 */
class ImportedNodeParametersTest {

  @Test
  void exportedValuesBecomeTheValuesTheGraphStores() throws Exception {
    JsonNode exported = json("{\"schema:name\": \"Round Trip\", \"createdOnTS\": 1792467393, \"isOpen\": true, "
        + "\"specialGroup\": null}");

    Map<String, Object> params = AbstractCypherParamBuilder.mapAllProperties(exported).asMap();

    assertEquals("Round Trip", params.get(key(NodeProperty.NAME)));
    assertEquals(1792467393L, params.get(key(NodeProperty.CREATED_ON_TS)));
    assertEquals(true, params.get(key(NodeProperty.IS_OPEN)));
    assertTrue(params.containsKey(key(NodeProperty.SPECIAL_GROUP)));
    assertNull(params.get(key(NodeProperty.SPECIAL_GROUP)));
  }

  @Test
  void aListBecomesAListOfPlainValues() throws Exception {
    CypherParameters params = new CypherParameters();
    params.put(NodeProperty.NAME, json("[\"a\", 1]"));
    assertEquals(List.of("a", 1L), params.asMap().get(key(NodeProperty.NAME)));
  }

  /** A group imported from an export gets the lower-cased name that lookups match, as a created group does. */
  @Test
  void anImportedGroupIsFoundByName() throws Exception {
    JsonNode exported = json("{\"@id\": \"https://repo.metadatacenter.orgx/groups/g\", \"schema:name\": \"Round Trippers\", "
        + "\"pav:createdBy\": \"u\", \"oslc:modifiedBy\": \"u\"}");
    CypherParameters params = AbstractCypherParamBuilder.mapAllProperties(exported);
    CypherParamBuilderGroup.tweakGroupProperties(exported, params);
    assertEquals("round trippers", params.asMap().get(key(NodeProperty.NAME_LOWER)));
    assertEquals("u", params.asMap().get(key(NodeProperty.CREATED_BY)), "the creator survives");
    assertEquals("u", params.asMap().get(key(NodeProperty.LAST_UPDATED_BY)), "the last modifier survives");
    assertTrue(CypherParamBuilderGroup.getGroupByName("Round Trippers").asMap().containsValue("round trippers"));
  }

  /**
   * A user is recreated from its exported node, which holds its keys, roles and preferences in the shapes the graph
   * stores, its provenance, and its home folder.
   */
  @Test
  void anImportedUserKeepsItsHomeFolderAndIsFoundByName() throws Exception {
    JsonNode exported = json("{\"@id\": \"https://metadatacenter.org/users/u\", \"schema:name\": \"Ada Lovelace\", "
        + "\"apiKeys\": [\"k\"], \"apiKeyMap\": {\"k\": {\"key\": \"k\"}}, \"roles\": [\"defaultUser\"], "
        + "\"homeFolderId\": \"https://repo.metadatacenter.orgx/folders/h\", \"createdOnTS\": 1792467761}");
    CypherParameters params = AbstractCypherParamBuilder.mapAllProperties(exported);
    CypherParamBuilderUser.tweakUserProperties(exported, params);
    Map<String, Object> map = params.asMap();
    assertEquals("ada lovelace", map.get(key(NodeProperty.NAME_LOWER)));
    assertEquals("https://repo.metadatacenter.orgx/folders/h", map.get(key(NodeProperty.HOME_FOLDER_ID)));
    assertEquals(List.of("k"), map.get(key(NodeProperty.API_KEYS)));
    assertEquals("{\"k\":{\"key\":\"k\"}}", map.get(key(NodeProperty.API_KEY_MAP)), "a map is stored as its JSON");
    assertEquals(1792467761L, map.get(key(NodeProperty.CREATED_ON_TS)), "the creation time is kept");
    assertTrue(org.metadatacenter.server.neo4j.cypher.query.CypherQueryBuilderUser.importUser()
        .contains(NodeProperty.HOME_FOLDER_ID.getValue()));
  }

  /** An exported folder holds no parent identifier, yet the folder query needs the parameter, even unset. */
  @Test
  void anImportedFolderHasEveryParameterItsQueryNeeds() throws Exception {
    JsonNode exported = json("{\"@id\": \"https://repo.metadatacenter.orgx/folders/f\", \"schema:name\": \"Round Trip\", "
        + "\"schema:description\": \"\", \"pav:createdBy\": \"u\", \"oslc:modifiedBy\": \"u\", "
        + "\"pav:createdOn\": \"2026-10-19T00:00:00-07:00\", \"pav:lastUpdatedOn\": \"2026-10-19T00:00:00-07:00\"}");
    CypherParameters params = AbstractCypherParamBuilder.mapAllProperties(exported);
    CypherParamBuilderGraph.tweakNodeProperties(exported, params);
    Map<String, Object> map = params.asMap();
    assertTrue(map.containsKey(key(NodeProperty.PARENT_FOLDER_ID)), map.toString());
    assertEquals("round trip", map.get(key(NodeProperty.NAME_LOWER)));
  }

  /** A schema artifact's status and previous version reach the query under the names it gives them. */
  @Test
  void anImportedArtifactKeepsItsStatusAndPreviousVersion() throws Exception {
    JsonNode exported = json("{\"@id\": \"https://repo.metadatacenter.orgx/templates/t2\", \"schema:name\": \"T\", "
        + "\"bibo:status\": \"bibo:published\", "
        + "\"pav:previousVersion\": \"https://repo.metadatacenter.orgx/templates/t1\"}");
    CypherParameters params = AbstractCypherParamBuilder.mapAllProperties(exported);
    CypherParamBuilderGraph.tweakNodeProperties(exported, params);
    Map<String, Object> map = params.asMap();
    assertEquals("bibo:published", map.get(ParameterPlaceholder.PUBLICATION_STATUS.getValue()), map.toString());
    assertEquals("https://repo.metadatacenter.orgx/templates/t1", map.get(ParameterPlaceholder.PREVIOUS_VERSION.getValue()));
  }

  private static JsonNode json(String text) throws Exception {
    return JsonMapper.STRICT_MAPPER.readTree(text);
  }

  private static String key(NodeProperty property) {
    return Neo4JUtil.escapePropertyName(property.getValue());
  }
}
