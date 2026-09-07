package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherCategoryPermissionQuerySemanticsTest {

  @Test
  void authoritySeparatesDirectOwnershipFromAncestorOwnership() {
    String query = CypherQueryBuilderCategoryPermission.getAuthority();

    assertTrue(query.contains(
        "(user)-[directOwnership:<REL.OWNSCATEGORY>]->(category)"), query);
    assertTrue(query.contains(
        "count(directOwnership) > 0 AS owner"), query);
    assertTrue(query.contains(
        "(user)-[:<REL.OWNSCATEGORY>]->(ancestor:<LABEL.CATEGORY>)"), query);
    assertTrue(query.contains(
        "-[:<REL.CONTAINSCATEGORY>*1..]->(category)"), query);
    assertTrue(query.contains(
        "count(DISTINCT ancestor) > 0 AS ancestorOwner"), query);
    assertTrue(query.contains(
        "RETURN owner, ancestorOwner, collect(DISTINCT type(grant)) AS roleRelations"), query);
    assertFalse(query.contains(
        "-[:<REL.CONTAINSCATEGORY>*0..]->(category)\n        WITH user, category, count"), query);
  }
}
