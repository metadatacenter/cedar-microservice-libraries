package org.metadatacenter.server.dao;

/**
 * The template store refused to delete or restructure a template because instances reference it: stored
 * ones, or a write of one that has reserved the template and may still commit.
 *
 * <p>It is a revision conflict as well, so code that handles only conflicts keeps refusing the write.
 * Code that tells the two apart can say why. A conflict means the template moved on, and a retry against
 * its current revision may succeed. A reference means the template's own state refuses the write, and a
 * retry is refused again until the references are gone.
 */
public class TemplateReferencedException extends ArtifactRevisionConflictException {

  public TemplateReferencedException(String message) {
    super(message, null);
  }
}
