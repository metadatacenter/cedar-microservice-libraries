package org.metadatacenter.server;

import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.security.model.user.CedarUser;

public interface AdminServiceSession {

  void ensureGlobalObjectsExists();

  void ensureCaDSRObjectsExists(CedarUser caDSRAdmin, UserServiceSession userSession);

  boolean wipeAllData();

  boolean wipeAllCategories();

  boolean createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property);

  boolean createIndex(NodeLabel nodeLabel, NodeProperty property);

  boolean removeAllConstraintsAndIndices();
}
