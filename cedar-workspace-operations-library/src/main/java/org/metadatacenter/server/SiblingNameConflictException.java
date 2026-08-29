package org.metadatacenter.server;

/**
 * Raised when Neo4j rejects two case-insensitively equal names under the same folder or category.
 * The database constraint is the final authority; REST existence checks remain only a fast path.
 */
public class SiblingNameConflictException extends RuntimeException {

  public SiblingNameConflictException(Throwable cause) {
    super("A sibling with the same case-insensitive name already exists", cause);
  }
}
