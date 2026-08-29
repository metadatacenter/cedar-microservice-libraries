package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherGroupMembershipQuerySemanticsTest {

  @Test
  void newGroupsStartWithMembershipRevisionOne() {
    assertTrue(CypherQueryBuilderGroup.createGroupWithAdministrator()
        .contains("_cedarMembershipRevision:1"));
  }

  @Test
  void replacementValidatesUsersBeforeDeletingAndReturnsItsPostImage() {
    String query = CypherQueryBuilderGroup.replaceGroupUsers();

    int validateUsers = query.indexOf("WHERE size(requestedUsers) = size({<PH.USER_ID_LIST>})");
    int deleteOld = query.indexOf("DELETE oldRelation");
    assertTrue(validateUsers >= 0 && validateUsers < deleteOld, query);
    assertTrue(query.contains("MERGE (user)-[:<REL.ADMINISTERS>]->(group)"), query);
    assertTrue(query.contains("MERGE (user)-[:<REL.MEMBEROF>]->(group)"), query);
    assertTrue(query.contains("SET group._cedarMembershipRevision = {<PH.CURRENT_REVISION>} + 1"), query);
    assertTrue(query.contains("RETURN group._cedarMembershipRevision AS revision"), query);
  }

  @Test
  void membershipReadReturnsBodyAndRevisionFromOneStatement() {
    String query = CypherQueryBuilderGroup.getVersionedGroupUsers();

    assertTrue(query.contains("coalesce(group._cedarMembershipRevision, 1) AS revision"), query);
    assertTrue(query.contains("user AS user"), query);
    assertTrue(query.contains("AS administrator"), query);
    assertTrue(query.contains("AS member"), query);
  }

  @Test
  void directEverybodyMembershipCreationAlsoAdvancesTheAggregateRevision() {
    String query = CypherQueryBuilderUser.addUserToGroup();

    assertTrue(query.contains("ON CREATE SET group._cedarMembershipRevision"), query);
    assertTrue(query.contains("coalesce(group._cedarMembershipRevision, 1) + 1"), query);
  }
}
