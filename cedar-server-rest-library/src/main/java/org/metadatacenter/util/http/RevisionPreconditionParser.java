package org.metadatacenter.util.http;

import org.metadatacenter.server.RevisionPrecondition;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses CEDAR's revision-bearing strong ETags from an If-Match field value. */
public final class RevisionPreconditionParser {

  private static final Pattern REVISION_STRONG_ETAG =
      Pattern.compile("\\\"([0-9]+)(?:-([A-Za-z0-9][A-Za-z0-9._-]*))?\\\"");
  private static final Pattern REPRESENTATION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*");

  private RevisionPreconditionParser() {
  }

  public static RevisionPrecondition parse(String ifMatch) {
    if ("*".equals(ifMatch.trim())) {
      return RevisionPrecondition.any();
    }
    Set<Long> revisions = new HashSet<>();
    for (String candidate : ifMatch.split(",")) {
      Matcher matcher = REVISION_STRONG_ETAG.matcher(candidate.trim());
      if (matcher.matches()) {
        try {
          revisions.add(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException ignored) {
          // A syntactically valid but out-of-range tag cannot match any stored long revision.
        }
      }
    }
    return revisions.isEmpty() ? RevisionPrecondition.none()
        : new RevisionPrecondition(false, revisions);
  }

  public static String format(long revision) {
    return "\"" + revision + "\"";
  }

  /**
   * Formats a strong validator for a non-canonical representation of a revision. The suffix keeps
   * byte-different YAML, compact YAML, JSON, and RDF renderings from claiming the same strong ETag,
   * while {@link #parse(String)} still recovers the datastore revision used for write exclusion.
   */
  public static String format(long revision, String representation) {
    if (representation == null || !REPRESENTATION.matcher(representation).matches()) {
      throw new IllegalArgumentException("Invalid ETag representation suffix: " + representation);
    }
    return "\"" + revision + "-" + representation + "\"";
  }
}
