package org.metadatacenter.server.logging;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.server.logging.model.AppLogParam;
import org.metadatacenter.server.logging.model.AppLogSubType;
import org.metadatacenter.server.logging.model.AppLogType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the filter keeps matters more than what it drops. log_cypher is the input to the hourly
 * rollups, the query catalog and the outlier tables, so an exclusion that reached past the named
 * methods would silently empty an analysis that reads them.
 */
class CypherLogFilterTest {

  private static AppLogMessage cypher(String className, String methodName) {
    AppLogMessage message = new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.CYPHER_QUERY,
        AppLogSubType.FULL, "global-1", "local-1");
    if (className != null) {
      message.param(AppLogParam.CLASS_NAME, className);
    }
    if (methodName != null) {
      message.param(AppLogParam.METHOD_NAME, methodName);
    }
    return message;
  }

  private static AppLogMessage request() {
    return new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.REQUEST_FILTER,
        AppLogSubType.START, "global-1", "local-1");
  }

  private static final String PROXY_USER = "org.metadatacenter.server.neo4j.proxy.Neo4JProxyUser";
  private static final String PERMISSION_SERVICE =
      "org.metadatacenter.server.neo4j.proxy.Neo4JUserSessionResourcePermissionService";

  @Test
  void theDefaultExcludesBothAuthenticationLookups() {
    CypherLogFilter filter = new CypherLogFilter(null);

    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserByApiKey")), "the apiKey lookup");
    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserById")), "the Keycloak token lookup");
    assertEquals(2, filter.getSuppressedEventCount());
  }

  @Test
  void theDefaultKeepsEveryOtherCypherQuery() {
    CypherLogFilter filter = new CypherLogFilter(null);

    assertTrue(filter.accepts(cypher(PERMISSION_SERVICE, "getResourceMaterializedPermission")));
    assertTrue(filter.accepts(cypher(PROXY_USER, "setHomeFolderId")));
    assertEquals(0, filter.getSuppressedEventCount());
  }

  /**
   * The parked messages on both hosts were cypherQuery with null request ids. Excluding a type is
   * not a reason to stop seeing the queries that fail to be written.
   */
  @Test
  void aQueryWhoseOriginIsUnknownIsKept() {
    CypherLogFilter filter = new CypherLogFilter(null);

    assertTrue(filter.accepts(cypher(PROXY_USER, null)), "no method name recorded");
    assertTrue(filter.accepts(cypher(null, null)), "neither name recorded");
    assertEquals(0, filter.getSuppressedEventCount());
  }

  @Test
  void aNonCypherMessageIsNeverRefused() {
    CypherLogFilter excludeEverything = new CypherLogFilter(CypherLogFilter.EXCLUDE_ALL);

    assertTrue(excludeEverything.accepts(request()));
    assertEquals(0, excludeEverything.getSuppressedEventCount());
  }

  @Test
  void theBluntFilterIsStillAvailable() {
    CypherLogFilter filter = new CypherLogFilter(CypherLogFilter.EXCLUDE_ALL);

    assertFalse(filter.accepts(cypher(PERMISSION_SERVICE, "getResourceMaterializedPermission")));
    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserByApiKey")));
    assertEquals(2, filter.getSuppressedEventCount());
  }

  @Test
  void exclusionsCanBeTurnedOffEntirely() {
    for (String value : new String[]{CypherLogFilter.EXCLUDE_NONE, "NONE", "", "   "}) {
      CypherLogFilter filter = new CypherLogFilter(value);
      assertTrue(filter.accepts(cypher(PROXY_USER, "findUserByApiKey")), "for value '" + value + "'");
      assertEquals(0, filter.getSuppressedEventCount());
    }
  }

  @Test
  void aBareMethodNameMatchesInAnyClass() {
    CypherLogFilter filter = new CypherLogFilter("findUserByApiKey");

    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserByApiKey")));
    assertFalse(filter.accepts(cypher("com.example.SomethingElse", "findUserByApiKey")));
    assertTrue(filter.accepts(cypher(PROXY_USER, "findUserById")));
  }

  @Test
  void aQualifiedEntryMatchesOnlyThatClass() {
    CypherLogFilter filter = new CypherLogFilter("Neo4JProxyUser.findUserById");

    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserById")));
    assertTrue(filter.accepts(cypher("com.example.OtherProxy", "findUserById")),
        "the same method name in a different class is a different query");
  }

  @Test
  void configurationIsWhitespaceAndCaseTolerant() {
    CypherLogFilter filter = new CypherLogFilter(" neo4jproxyuser.FINDUSERBYAPIKEY ,, findUserById ");

    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserByApiKey")));
    assertFalse(filter.accepts(cypher(PROXY_USER, "findUserById")));
    assertTrue(filter.accepts(cypher(PERMISSION_SERVICE, "getResourceMaterializedPermission")));
  }

  @Test
  void suppressionIsCountedRatherThanSilent() {
    CypherLogFilter filter = new CypherLogFilter(null);

    for (int i = 0; i < 5; i++) {
      filter.accepts(cypher(PROXY_USER, "findUserByApiKey"));
      filter.accepts(cypher(PERMISSION_SERVICE, "getResourceMaterializedPermission"));
    }

    assertEquals(5, filter.getSuppressedEventCount(), "only the excluded half is counted");
  }

  @Test
  void aNullMessageIsNotTreatedAsAnExclusion() {
    CypherLogFilter filter = new CypherLogFilter(CypherLogFilter.EXCLUDE_ALL);

    assertTrue(filter.accepts(null));
    assertEquals(0, filter.getSuppressedEventCount());
  }
}
