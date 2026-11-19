package org.metadatacenter.server.neo4j;

import org.metadatacenter.server.neo4j.cypher.NodeProperty;

import java.util.List;

/**
 * The uniqueness constraints and indexes of a provisioned CEDAR graph, which the admin tool's
 * {@code graphDb-createIndicesAndConstraints} creates. The embedded test graph creates the constraints
 * when it is seeded, so a suite meets the uniqueness rules the deployed graph enforces. The constraints
 * the servers create for themselves when they start are not listed here.
 */
public final class GraphSchema {

  /** A uniqueness rule over one property, or over the combination of several, of every node with a label. */
  public record UniqueConstraint(NodeLabel label, List<NodeProperty> properties) {
    public UniqueConstraint {
      properties = List.copyOf(properties);
    }
  }

  /** An index over one property of every node with a label. */
  public record Index(NodeLabel label, NodeProperty property) {
  }

  /**
   * The uniqueness constraints: a resource's identifier; a group's name in any case, which a lookup
   * before the write cannot enforce against a concurrent create; and a folder's or a category's name
   * in any case among the children of one parent.
   */
  public static final List<UniqueConstraint> UNIQUE_CONSTRAINTS = List.of(
      new UniqueConstraint(NodeLabel.RESOURCE, List.of(NodeProperty.ID)),
      new UniqueConstraint(NodeLabel.GROUP, List.of(NodeProperty.NAME_LOWER)),
      new UniqueConstraint(NodeLabel.CATEGORY, List.of(NodeProperty.PARENT_CATEGORY_ID, NodeProperty.NAME_LOWER)),
      new UniqueConstraint(NodeLabel.FOLDER, List.of(NodeProperty.PARENT_FOLDER_ID, NodeProperty.NAME_LOWER)));

  /** The indexes, in the order the admin tool creates them. */
  public static final List<Index> INDEXES = List.of(
      new Index(NodeLabel.RESOURCE, NodeProperty.OWNED_BY),
      new Index(NodeLabel.RESOURCE, NodeProperty.RESOURCE_TYPE),
      new Index(NodeLabel.RESOURCE, NodeProperty.NODE_SORT_ORDER),
      new Index(NodeLabel.RESOURCE, NodeProperty.NAME),
      new Index(NodeLabel.RESOURCE, NodeProperty.NAME_LOWER),
      new Index(NodeLabel.RESOURCE, NodeProperty.CREATED_ON_TS),
      new Index(NodeLabel.RESOURCE, NodeProperty.SPECIAL_FOLDER),
      new Index(NodeLabel.RESOURCE, NodeProperty.IS_BASED_ON),
      new Index(NodeLabel.RESOURCE, NodeProperty.IS_USER_HOME),
      new Index(NodeLabel.GROUP, NodeProperty.ID),
      new Index(NodeLabel.GROUP, NodeProperty.NAME),
      new Index(NodeLabel.GROUP, NodeProperty.SPECIAL_GROUP),
      new Index(NodeLabel.USER, NodeProperty.ID),
      new Index(NodeLabel.USER, NodeProperty.NAME),
      new Index(NodeLabel.USER, NodeProperty.NAME_LOWER),
      new Index(NodeLabel.USER, NodeProperty.API_KEYS),
      new Index(NodeLabel.CATEGORY, NodeProperty.ID),
      new Index(NodeLabel.CATEGORY, NodeProperty.NAME),
      new Index(NodeLabel.FOLDER, NodeProperty.ID),
      new Index(NodeLabel.FOLDER, NodeProperty.OWNED_BY),
      new Index(NodeLabel.FOLDER, NodeProperty.RESOURCE_TYPE),
      new Index(NodeLabel.FOLDER, NodeProperty.NODE_SORT_ORDER),
      new Index(NodeLabel.FOLDER, NodeProperty.NAME),
      new Index(NodeLabel.FOLDER, NodeProperty.CREATED_ON_TS),
      new Index(NodeLabel.FOLDER, NodeProperty.LAST_UPDATED_ON_TS),
      new Index(NodeLabel.FOLDER, NodeProperty.IS_ROOT),
      new Index(NodeLabel.FOLDER, NodeProperty.IS_SYSTEM),
      new Index(NodeLabel.FOLDER, NodeProperty.IS_USER_HOME),
      new Index(NodeLabel.FOLDER, NodeProperty.HOME_OF),
      new Index(NodeLabel.FOLDER, NodeProperty.EVERYBODY_PERMISSION),
      new Index(NodeLabel.FOLDER, NodeProperty.SPECIAL_FOLDER),
      new Index(NodeLabel.ARTIFACT, NodeProperty.ID),
      new Index(NodeLabel.ARTIFACT, NodeProperty.OWNED_BY),
      new Index(NodeLabel.ARTIFACT, NodeProperty.RESOURCE_TYPE),
      new Index(NodeLabel.ARTIFACT, NodeProperty.NODE_SORT_ORDER),
      new Index(NodeLabel.ARTIFACT, NodeProperty.NAME),
      new Index(NodeLabel.ARTIFACT, NodeProperty.NAME_LOWER),
      new Index(NodeLabel.ARTIFACT, NodeProperty.CREATED_ON_TS),
      new Index(NodeLabel.ARTIFACT, NodeProperty.LAST_UPDATED_ON_TS),
      new Index(NodeLabel.ARTIFACT, NodeProperty.IS_BASED_ON),
      new Index(NodeLabel.ARTIFACT, NodeProperty.DERIVED_FROM),
      new Index(NodeLabel.ARTIFACT, NodeProperty.VERSION),
      new Index(NodeLabel.ARTIFACT, NodeProperty.PREVIOUS_VERSION),
      new Index(NodeLabel.ARTIFACT, NodeProperty.PUBLICATION_STATUS),
      new Index(NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_VERSION),
      new Index(NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_PUBLISHED_VERSION),
      new Index(NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_DRAFT_VERSION),
      new Index(NodeLabel.ARTIFACT, NodeProperty.EVERYBODY_PERMISSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.ID),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.RESOURCE_TYPE),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NODE_SORT_ORDER),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NAME),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NAME_LOWER),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.EVERYBODY_PERMISSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_USER_HOME),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.CREATED_ON_TS),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.VERSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.PREVIOUS_VERSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.PUBLICATION_STATUS),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_VERSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_PUBLISHED_VERSION),
      new Index(NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_DRAFT_VERSION),
      new Index(NodeLabel.FIELD, NodeProperty.ID),
      new Index(NodeLabel.ELEMENT, NodeProperty.ID),
      new Index(NodeLabel.TEMPLATE, NodeProperty.ID),
      new Index(NodeLabel.INSTANCE, NodeProperty.ID));

  private GraphSchema() {
  }
}
