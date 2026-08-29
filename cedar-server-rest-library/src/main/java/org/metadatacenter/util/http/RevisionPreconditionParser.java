package org.metadatacenter.util.http;

import org.metadatacenter.server.RevisionPrecondition;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses CEDAR's numeric strong ETags from an If-Match field value. */
public final class RevisionPreconditionParser {

  private static final Pattern NUMERIC_STRONG_ETAG = Pattern.compile("\\\"([0-9]+)\\\"");

  private RevisionPreconditionParser() {
  }

  public static RevisionPrecondition parse(String ifMatch) {
    if ("*".equals(ifMatch.trim())) {
      return RevisionPrecondition.any();
    }
    Set<Long> revisions = new HashSet<>();
    for (String candidate : ifMatch.split(",")) {
      Matcher matcher = NUMERIC_STRONG_ETAG.matcher(candidate.trim());
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
}
