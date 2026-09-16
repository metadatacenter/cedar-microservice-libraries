package org.metadatacenter.server.neo4j.cypher.query;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.neo4j.CypherQueryWithParameters;
import org.metadatacenter.server.neo4j.parameter.CypherParameters;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.harness.Neo4j;
import org.neo4j.harness.Neo4jBuilders;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CypherPermissionQueryEquivalenceTest {

  private static final String RESOURCE_ID = "resource-special-nested";
  private static final String ROOT_NAME = "CEDAR";

  private static Neo4j neo4j;
  private static Driver driver;

  @BeforeAll
  static void startGraph() {
    neo4j = Neo4jBuilders.newInProcessBuilder().withDisabledServer().build();
    driver = GraphDatabase.driver(neo4j.boltURI(), AuthTokens.none());
    try (var session = driver.session()) {
      session.run("""
          CREATE (owner:User:Resource {_id: 'user-owner'})
          CREATE (viewer:User:Resource {_id: 'user-viewer'})
          CREATE (editor:User:Resource {_id: 'user-editor'})
          CREATE (manager:User:Resource {_id: 'user-manager'})
          CREATE (overlap:User:Resource {_id: 'user-overlap'})
          CREATE (outsider:User:Resource {_id: 'user-outsider'})
          CREATE (otherOwner:User:Resource {_id: 'user-other-owner'})
          CREATE (group:Group:Resource {_id: 'group-shared'})
          CREATE (root:Folder:FileSystemResource:Resource {
            _id: 'folder-root', schema_name: 'CEDAR', schema_name_lower: 'cedar',
            nodeSortOrder: 1, isRoot: true, isOpen: true})
          CREATE (shared:Folder:FileSystemResource:Resource {
            _id: 'folder-shared', schema_name: 'Shared', schema_name_lower: 'shared',
            nodeSortOrder: 1, isOpen: true})
          CREATE (nested:Folder:FileSystemResource:Resource {
            _id: 'folder-nested', schema_name: 'Nested', schema_name_lower: 'nested',
            nodeSortOrder: 1})
          CREATE (specialDirect:Folder:FileSystemResource:Resource {
            _id: 'resource-special-direct', schema_name: 'Direct special',
            schema_name_lower: 'direct special', nodeSortOrder: 1, specialFolder: 'direct'})
          CREATE (specialNested:Folder:FileSystemResource:Resource {
            _id: 'resource-special-nested', schema_name: 'Nested special',
            schema_name_lower: 'nested special', nodeSortOrder: 1, specialFolder: 'nested'})
          CREATE (privateRoot:Folder:FileSystemResource:Resource {
            _id: 'folder-private', schema_name: 'Private', schema_name_lower: 'private',
            nodeSortOrder: 1})
          CREATE (privateSpecial:Folder:FileSystemResource:Resource {
            _id: 'resource-special-private', schema_name: 'Private special',
            schema_name_lower: 'private special', nodeSortOrder: 1, specialFolder: 'private'})
          CREATE (root)-[:CONTAINS]->(shared)
          CREATE (shared)-[:CONTAINS]->(nested)
          CREATE (shared)-[:CONTAINS]->(specialDirect)
          CREATE (nested)-[:CONTAINS]->(specialNested)
          CREATE (root)-[:CONTAINS]->(privateRoot)
          CREATE (privateRoot)-[:CONTAINS]->(privateSpecial)
          CREATE (owner)-[:OWNS]->(shared)
          CREATE (otherOwner)-[:OWNS]->(privateRoot)
          CREATE (viewer)-[:CANREAD]->(shared)
          CREATE (editor)-[:EDITOR_ROLE]->(shared)
          CREATE (manager)-[:CANWRITE]->(shared)
          CREATE (overlap)-[:OWNS]->(shared)
          CREATE (overlap)-[:CANREAD]->(shared)
          CREATE (overlap)-[:MEMBEROF]->(group)
          CREATE (group)-[:CANREAD]->(root)
          CREATE (group)-[:CANREAD]->(shared)
          """).consume();
    }
  }

  @AfterAll
  static void stopGraph() {
    if (driver != null) {
      driver.close();
    }
    if (neo4j != null) {
      neo4j.close();
    }
  }

  @Test
  void specialFolderLookupAndCountPreserveVisibleResourceSets() {
    for (String userId : List.of("user-owner", "user-viewer", "user-editor", "user-manager",
        "user-overlap", "user-outsider")) {
      Map<String, Object> parameters = Map.of("userId", userId, "offset", 0, "limit", 100);
      List<String> oldIds = nodeIds(oldSpecialFoldersLookup(), parameters, "resource");
      List<String> newIds = nodeIds(
          CypherQueryBuilderResource.getSpecialFoldersLookupQuery(List.of("name"), true),
          parameters, "resource");

      assertEquals(oldIds, newIds, userId);
      long oldCount = firstLongOrMinusOne(oldSpecialFoldersCount(), Map.of("userId", userId));
      long newCount = firstLongOrMinusOne(
          CypherQueryBuilderResource.getSpecialFoldersCountQuery(true), Map.of("userId", userId));
      assertEquals(newIds.size(), newCount, userId + " lookup/count agreement");
      if (userId.equals("user-overlap")) {
        assertTrue(oldCount > newCount, "the old count must expose its overlapping-path overcount");
      } else {
        assertEquals(oldCount, newCount, userId);
      }
    }

    Map<String, Object> unrestricted = Map.of("offset", 0, "limit", 100);
    assertEquals(nodeIds(oldSpecialFoldersLookupUnrestricted(), unrestricted, "resource"),
        nodeIds(CypherQueryBuilderResource.getSpecialFoldersLookupQuery(List.of("name"), false),
            unrestricted, "resource"));
    assertEquals(firstLongOrMinusOne(oldSpecialFoldersCountUnrestricted(), Map.of()),
        firstLongOrMinusOne(CypherQueryBuilderResource.getSpecialFoldersCountQuery(false), Map.of()));
  }

  @Test
  void roleChecksPreserveExistenceAcrossOwnershipAndEveryGrantStrength() {
    assertRoleEquivalent("user-owner", "VIEWER");
    assertRoleEquivalent("user-owner", "EDITOR");
    assertRoleEquivalent("user-owner", "MANAGER");
    assertRoleEquivalent("user-viewer", "VIEWER");
    assertRoleEquivalent("user-viewer", "EDITOR");
    assertRoleEquivalent("user-editor", "EDITOR");
    assertRoleEquivalent("user-manager", "MANAGER");
    assertRoleEquivalent("user-overlap", "VIEWER");
    assertRoleEquivalent("user-outsider", "VIEWER");
  }

  @Test
  void transitiveUserMaterializationPreservesIdentitySetsWithoutCrossProducts() {
    assertEquals(stringSet(oldTransitiveUsers("CANREAD|VIEWER_ROLE"), RESOURCE_ID),
        stringSet(CypherQueryBuilderFilesystemResourcePermission
            .getUserIdsWithTransitiveViewerRoleOnFilesystemResource(), RESOURCE_ID));
    assertEquals(stringSet(oldTransitiveUsers("EDITOR_ROLE"), RESOURCE_ID),
        stringSet(CypherQueryBuilderFilesystemResourcePermission
            .getUserIdsWithTransitiveEditorRoleOnFilesystemResource(), RESOURCE_ID));
    assertEquals(stringSet(oldTransitiveUsers("CANWRITE|MANAGER_ROLE"), RESOURCE_ID),
        stringSet(CypherQueryBuilderFilesystemResourcePermission
            .getUserIdsWithTransitiveManagerRoleOnFilesystemResource(), RESOURCE_ID));
  }

  @Test
  void transitiveGroupMaterializationOnlyRemovesDuplicateRows() {
    List<String> oldRows = strings(oldTransitiveGroups("CANREAD|VIEWER_ROLE"),
        Map.of("fsResourceId", RESOURCE_ID));
    List<String> newRows = strings(CypherQueryBuilderFilesystemResourcePermission
        .getGroupIdsWithTransitiveViewerRoleOnFilesystemResource(),
        Map.of("fsResourceId", RESOURCE_ID));

    assertEquals(new HashSet<>(oldRows), new HashSet<>(newRows));
    assertTrue(oldRows.size() > newRows.size(),
        "the fixture must exercise duplicate group rows removed by DISTINCT");
  }

  @Test
  void implicitOpenCheckPreservesBooleanMeaningWhileCollapsingMatchingPaths() {
    Map<String, Object> openParameters = Map.of("name", ROOT_NAME, "_id", RESOURCE_ID);
    int oldOpenRows = rowCount(oldImplicitOpen(), openParameters);
    int newOpenRows = rowCount(CypherQueryBuilderFilesystemResource.isFileSystemResourceOpenImplicitly(),
        openParameters);

    assertEquals(1, newOpenRows);
    assertTrue(oldOpenRows > newOpenRows,
        "the fixture must exercise multiple matching paths collapsed by EXISTS");
    assertEquals(oldOpenRows > 0, newOpenRows > 0);

    Map<String, Object> closedParameters = Map.of(
        "name", "Private", "_id", "resource-special-private");
    assertFalse(rowCount(oldImplicitOpen(), closedParameters) > 0);
    assertEquals(rowCount(oldImplicitOpen(), closedParameters),
        rowCount(CypherQueryBuilderFilesystemResource.isFileSystemResourceOpenImplicitly(),
            closedParameters));
  }

  private static void assertRoleEquivalent(String userId, String role) {
    String labels = switch (role) {
      case "VIEWER" -> "CANREAD|VIEWER_ROLE|EDITOR_ROLE|CANWRITE|MANAGER_ROLE";
      case "EDITOR" -> "EDITOR_ROLE|CANWRITE|MANAGER_ROLE";
      case "MANAGER" -> "CANWRITE|MANAGER_ROLE";
      default -> throw new IllegalArgumentException(role);
    };
    String replacement = switch (role) {
      case "VIEWER" -> CypherQueryBuilderFilesystemResourcePermission
          .userHasViewerRoleOnFilesystemResource();
      case "EDITOR" -> CypherQueryBuilderFilesystemResourcePermission
          .userHasEditorRoleOnFilesystemResource();
      case "MANAGER" -> CypherQueryBuilderFilesystemResourcePermission
          .userHasManagerRoleOnFilesystemResource();
      default -> throw new IllegalArgumentException(role);
    };
    Map<String, Object> parameters = Map.of("userId", userId, "fsResourceId", RESOURCE_ID);
    assertEquals(rowCount(oldRoleCheck(labels), parameters) > 0,
        rowCount(replacement, parameters) > 0, userId + " " + role);
  }

  private static Set<String> stringSet(String query, String resourceId) {
    return new HashSet<>(strings(query, Map.of("fsResourceId", resourceId)));
  }

  private static List<String> nodeIds(String template, Map<String, Object> parameters, String alias) {
    return records(template, parameters).stream()
        .map(record -> record.get(alias).asNode().get("_id").asString())
        .toList();
  }

  private static List<String> strings(String template, Map<String, Object> parameters) {
    return records(template, parameters).stream().map(record -> record.get(0).asString()).toList();
  }

  private static long firstLongOrMinusOne(String template, Map<String, Object> parameters) {
    List<Record> result = records(template, parameters);
    return result.isEmpty() ? -1 : result.get(0).get(0).asLong();
  }

  private static int rowCount(String template, Map<String, Object> parameters) {
    return records(template, parameters).size();
  }

  private static List<Record> records(String template, Map<String, Object> parameters) {
    String query = new CypherQueryWithParameters(template, new CypherParameters()).getRunnableQuery();
    try (var session = driver.session()) {
      return session.executeRead(tx -> tx.run(query, parameters).list());
    }
  }

  private static String oldSpecialFoldersLookup() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.RESOURCE>)
        WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL
        OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
        OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:CANREAD|VIEWER_ROLE|EDITOR_ROLE|CANWRITE|MANAGER_ROLE]-()<-[:MEMBEROF*0..1]-(user:User)
        WITH user, resource, p1, p2
        WHERE p1 IS NOT NULL OR p2 IS NOT NULL
        RETURN DISTINCT resource
        ORDER BY resource.<PROP.NODE_SORT_ORDER>, resource.<PROP.NAME_LOWER> ASC, resource.<PROP.ID>
        SKIP $offset LIMIT $limit
        """;
  }

  private static String oldSpecialFoldersCount() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        RETURN COUNT {
          MATCH (resource:<LABEL.RESOURCE>)
          WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL
          OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
          OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:CANREAD|VIEWER_ROLE|EDITOR_ROLE|CANWRITE|MANAGER_ROLE]-()<-[:MEMBEROF*0..1]-(user:User)
          WITH user, resource, p1, p2
          WHERE p1 IS NOT NULL OR p2 IS NOT NULL
          RETURN DISTINCT resource
        }
        """;
  }

  private static String oldSpecialFoldersLookupUnrestricted() {
    return """
        MATCH (resource:<LABEL.RESOURCE>)
        WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL
        RETURN DISTINCT resource
        ORDER BY resource.<PROP.NODE_SORT_ORDER>, resource.<PROP.NAME_LOWER> ASC, resource.<PROP.ID>
        SKIP $offset LIMIT $limit
        """;
  }

  private static String oldSpecialFoldersCountUnrestricted() {
    return """
        RETURN COUNT {
          MATCH (resource:<LABEL.RESOURCE>)
          WHERE resource.<PROP.SPECIAL_FOLDER> IS NOT NULL
          RETURN DISTINCT resource
        }
        """;
  }

  private static String oldRoleCheck(String grantLabels) {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH p1 = (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(user:User)
        OPTIONAL MATCH p2 = (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(user:User)
        WITH user, resource, p1, p2
        WHERE p1 IS NOT NULL OR p2 IS NOT NULL
        RETURN DISTINCT user
        """.formatted(grantLabels);
  }

  private static String oldTransitiveUsers(String roleLabels) {
    return """
        MATCH (resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        OPTIONAL MATCH (resource)<-[:CONTAINS*0..]-()<-[:OWNS]-(owner:User)
        OPTIONAL MATCH (resource)<-[:CONTAINS*0..]-()<-[:%s]-()<-[:MEMBEROF*0..1]-(grantee:User)
        WITH collect(DISTINCT owner) + collect(DISTINCT grantee) AS users
        UNWIND users AS user
        RETURN DISTINCT user.<PROP.ID>
        """.formatted(roleLabels);
  }

  private static String oldTransitiveGroups(String roleLabels) {
    return """
        MATCH (group:<LABEL.GROUP>)-[:%s]->()-[:<REL.CONTAINS>*0..]->(resource:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.FS_RESOURCE_ID>}})
        RETURN group.<PROP.ID>
        """.formatted(roleLabels);
  }

  private static String oldImplicitOpen() {
    return """
        MATCH (root:<LABEL.FOLDER> {<PROP.NAME>:{<PH.NAME>}}),
              (current:<LABEL.FILESYSTEM_RESOURCE> {<PROP.ID>:{<PH.ID>}}),
              path=((root)-[:<REL.CONTAINS>*0..]->(open {<PROP.IS_OPEN>:true})-[:<REL.CONTAINS>*0..]->(current))
        RETURN path
        """;
  }
}
