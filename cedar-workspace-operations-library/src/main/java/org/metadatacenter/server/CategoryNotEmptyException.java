package org.metadatacenter.server;

/**
 * Raised when category deletion is refused because the category still owns hierarchy or
 * classification relationships.
 */
public class CategoryNotEmptyException extends RuntimeException {

  private final long childCategoryCount;
  private final long artifactCount;

  public CategoryNotEmptyException(long childCategoryCount, long artifactCount) {
    super("Category has " + childCategoryCount + " child categories and " + artifactCount
        + " attached artifacts");
    this.childCategoryCount = childCategoryCount;
    this.artifactCount = artifactCount;
  }

  public long getChildCategoryCount() {
    return childCategoryCount;
  }

  public long getArtifactCount() {
    return artifactCount;
  }
}
