package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherUserProfileQuerySemanticsTest {

  @Test
  void profileUpdateWritesPreferencesAndTimestampsOnly() {
    String query = CypherQueryBuilderUser.updateUserProfile();

    assertTrue(query.contains(property(NodeProperty.UI_PREFERENCES)), query);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON)), query);
    assertTrue(query.contains(property(NodeProperty.LAST_UPDATED_ON_TS)), query);
    for (NodeProperty protectedProperty : List.of(NodeProperty.API_KEYS, NodeProperty.API_KEY_MAP,
        NodeProperty.ROLES, NodeProperty.PERMISSIONS, NodeProperty.EMAIL, NodeProperty.FIRST_NAME,
        NodeProperty.LAST_NAME, NodeProperty.HOME_FOLDER_ID)) {
      assertFalse(query.contains(property(protectedProperty)), query);
    }
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
}
