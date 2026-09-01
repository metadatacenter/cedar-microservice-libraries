package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherUserProfileQuerySemanticsTest {

  private static final List<NodeProperty> USER_VALUE_PROPERTIES = List.of(
      NodeProperty.API_KEYS, NodeProperty.API_KEY_MAP, NodeProperty.ROLES, NodeProperty.PERMISSIONS,
      NodeProperty.EMAIL, NodeProperty.FIRST_NAME, NodeProperty.LAST_NAME, NodeProperty.HOME_FOLDER_ID,
      NodeProperty.UI_PREFERENCES);

  @Test
  void profileUpdateWritesPreferencesAndTimestampsOnly() {
    String query = CypherQueryBuilderUser.updateUserProfile();

    assertTrue(query.contains(property(NodeProperty.UI_PREFERENCES)), query);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON)), query);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON_TS)), query);
    for (NodeProperty protectedProperty : USER_VALUE_PROPERTIES) {
      if (protectedProperty == NodeProperty.UI_PREFERENCES) {
        continue;
      }
      assertFalse(query.contains(property(protectedProperty)), query);
    }
  }

  @Test
  void homeFolderUpdateWritesOnlyTheHomeFolderAndTimestamps() {
    assertWritesOnly(CypherQueryBuilderUser.setUserHomeFolderId(), NodeProperty.HOME_FOLDER_ID);
  }

  @Test
  void authorizationUpdateWritesOnlyRolesPermissionsAndTimestamps() {
    assertWritesOnly(CypherQueryBuilderUser.replaceUserRolesAndPermissions(),
        NodeProperty.ROLES, NodeProperty.PERMISSIONS);
  }

  @Test
  void profileReadTakesTheUserWriteLockWithoutChangingPreferences() {
    String query = CypherQueryBuilderUser.lockAndReadUserProfile();

    assertTrue(query.contains(
        "SET user.<PROP.UI_PREFERENCES> = user.<PROP.UI_PREFERENCES>"), query);
    assertTrue(query.contains("RETURN user"), query);
  }

  private static String property(NodeProperty property) {
    return "user." + Neo4JUtil.escapePropertyName(property.getValue());
  }

  private static void assertWritesOnly(String query, NodeProperty... expectedProperties) {
    List<NodeProperty> expected = List.of(expectedProperties);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON)), query);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON_TS)), query);
    for (NodeProperty property : USER_VALUE_PROPERTIES) {
      if (expected.contains(property)) {
        assertTrue(query.contains(property(property)), query);
      } else {
        assertFalse(query.contains(property(property)), query);
      }
    }
  }
}
