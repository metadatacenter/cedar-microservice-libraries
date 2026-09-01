package org.metadatacenter.server;

import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.CedarUser;

import java.util.List;

public interface AdminServiceSession {

  void ensureGlobalObjectsExists();

  void ensureCaDSRObjectsExists(CedarUser caDSRAdmin, UserServiceSession userSession);

  boolean wipeAllData();

  boolean wipeAllCategories();

  boolean createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property);

  boolean createUniqueConstraint(NodeLabel nodeLabel, List<NodeProperty> properties);

  boolean backfillFolderParentIds();

  boolean createIndex(NodeLabel nodeLabel, NodeProperty property);

  boolean removeAllConstraintsAndIndices();
}
