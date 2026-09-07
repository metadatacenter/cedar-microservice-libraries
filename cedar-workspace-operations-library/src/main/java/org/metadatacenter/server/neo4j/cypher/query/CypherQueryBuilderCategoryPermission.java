package org.metadatacenter.server.neo4j.cypher.query;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.security.model.permission.category.CategoryRole;

public class CypherQueryBuilderCategoryPermission extends AbstractCypherQueryBuilder {

  public static String getVersionedPermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        OPTIONAL MATCH (owner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (principal)-[grant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(category)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS role,
          coalesce(category._cedarAclRevision, 1) AS revision
        """;
  }

  public static String lockPermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1)
        RETURN category._cedarAclRevision AS revision
        """;
  }

  public static String replacePermissions() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        MATCH (owner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (user:<LABEL.USER>)
        WHERE user.<PROP.ID> IN {<PH.USER_ID_LIST>}
        WITH category, owner, collect(DISTINCT user) AS users
        WHERE size(users) = size({<PH.USER_ID_LIST>})
        OPTIONAL MATCH (group:<LABEL.GROUP>)
        WHERE group.<PROP.ID> IN {<PH.GROUP_ID_LIST>}
        WITH category, owner, users, collect(DISTINCT group) AS groups
        WHERE size(groups) = size({<PH.GROUP_ID_LIST>})
        OPTIONAL MATCH ()-[oldGrant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(category)
        WITH category, owner, users, groups, collect(DISTINCT oldGrant) AS oldGrants
        FOREACH (grant IN oldGrants | DELETE grant)
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.VIEWER_USER_ID_LIST>}] |
          CREATE (user)-[:VIEWER_ROLE]->(category))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.ATTACH_USER_ID_LIST>}] |
          CREATE (user)-[:CANATTACHCATEGORY]->(category))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.EDITOR_USER_ID_LIST>}] |
          CREATE (user)-[:EDITOR_ROLE]->(category))
        FOREACH (user IN [candidate IN users WHERE candidate.<PROP.ID> IN {<PH.MANAGER_USER_ID_LIST>}] |
          CREATE (user)-[:CANWRITECATEGORY]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.VIEWER_GROUP_ID_LIST>}] |
          CREATE (group)-[:VIEWER_ROLE]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.ATTACH_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANATTACHCATEGORY]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.EDITOR_GROUP_ID_LIST>}] |
          CREATE (group)-[:EDITOR_ROLE]->(category))
        FOREACH (group IN [candidate IN groups WHERE candidate.<PROP.ID> IN {<PH.MANAGER_GROUP_ID_LIST>}] |
          CREATE (group)-[:CANWRITECATEGORY]->(category))
        SET category._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH category
        OPTIONAL MATCH (newOwner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (principal)-[grant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(category)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN newOwner AS owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS role,
          category._cedarAclRevision AS revision
        """;
  }

  public static String addRoleToCategoryForUser(CategoryRole role) {
    return "" +
        " MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})" +
        " MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})" +
        " MERGE (user)-[:" + RelationLabel.forCategoryRole(role) + "]->(category)" +
        " SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1" +
        " RETURN user";
  }

  public static String ensureViewerRoleForGroup() {
    return """
        MATCH (group:<LABEL.GROUP> {<PROP.ID>:{<PH.GROUP_ID>}})
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        MERGE (group)-[grant:VIEWER_ROLE]->(category)
        ON CREATE SET category._cedarAclRevision = coalesce(category._cedarAclRevision, 1) + 1
        RETURN group
        """;
  }

  public static String getAuthority() {
    return """
        MATCH (user:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        OPTIONAL MATCH (user)-[directOwnership:<REL.OWNSCATEGORY>]->(category)
        WITH user, category, count(directOwnership) > 0 AS owner
        OPTIONAL MATCH (user)-[:<REL.OWNSCATEGORY>]->(ancestor:<LABEL.CATEGORY>)
          -[:<REL.CONTAINSCATEGORY>*1..]->(category)
        WITH user, category, owner, count(DISTINCT ancestor) > 0 AS ancestorOwner
        OPTIONAL MATCH (user)-[:<REL.MEMBEROF>*0..1]->(principal)
          -[grant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(granted:<LABEL.CATEGORY>)
          -[:<REL.CONTAINSCATEGORY>*0..]->(category)
        RETURN owner, ancestorOwner, collect(DISTINCT type(grant)) AS roleRelations
        """;
  }

  public static String transferOwnership() {
    return """
        MATCH (category:<LABEL.CATEGORY> {<PROP.ID>:{<PH.CATEGORY_ID>}})
        MATCH (currentOwner:<LABEL.USER> {<PROP.ID>:{<PH.OWNER_ID>}})
          -[ownership:<REL.OWNSCATEGORY>]->(category)
        MATCH (newOwner:<LABEL.USER> {<PROP.ID>:{<PH.USER_ID>}})
        OPTIONAL MATCH (newOwner)-[redundant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(category)
        DELETE ownership, redundant
        CREATE (newOwner)-[:<REL.OWNSCATEGORY>]->(category)
        SET category.<PROP.OWNED_BY> = {<PH.USER_ID>},
            category._cedarAclRevision = {<PH.CURRENT_REVISION>} + 1
        WITH category
        MATCH (owner:<LABEL.USER>)-[:<REL.OWNSCATEGORY>]->(category)
        OPTIONAL MATCH (principal)-[grant:VIEWER_ROLE|CANATTACHCATEGORY|EDITOR_ROLE|CANWRITECATEGORY]->(category)
        WHERE principal IS NULL OR principal:<LABEL.USER> OR principal:<LABEL.GROUP>
        RETURN owner, principal,
          CASE WHEN principal:<LABEL.USER> THEN 'user'
               WHEN principal:<LABEL.GROUP> THEN 'group'
               ELSE null END AS principalType,
          CASE WHEN grant IS NULL THEN null ELSE type(grant) END AS role,
          category._cedarAclRevision AS revision
        """;
  }

}
