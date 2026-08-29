package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.server.RevisionPrecondition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevisionPreconditionParserTest {

  @Test
  void wildcardMatchesEveryCurrentRepresentation() {
    assertTrue(RevisionPreconditionParser.parse(" * ").matches(937));
  }

  @Test
  void anyStrongNumericTagMayMatch() {
    RevisionPrecondition parsed = RevisionPreconditionParser.parse("\"3\", \"7\"");
    assertTrue(parsed.matches(3));
    assertTrue(parsed.matches(7));
    assertFalse(parsed.matches(4));
  }

  @Test
  void weakAndNonNumericTagsDoNotSatisfyIfMatch() {
    RevisionPrecondition parsed = RevisionPreconditionParser.parse("W/\"3\", \"opaque\"");
    assertFalse(parsed.matches(3));
  }

  @Test
  void formatsNumericStrongEntityTags() {
    assertEquals("\"12\"", RevisionPreconditionParser.format(12));
  }
}
