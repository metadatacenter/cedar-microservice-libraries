package org.metadatacenter.model.request;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Which search the resource server runs for a combination of query parameters. The answer chooses
 * between the graph-backed listings and the index-backed search, so the precedence matters: an
 * identifier wins over everything, a term wins over every filter, and a filter value the detector
 * does not know falls through rather than selecting a listing. An empty parameter counts as absent,
 * and a term of {@code *} is no term at all.
 */
class NodeListQueryTypeDetectorTest {

  @ParameterizedTest(name = "q={0} id={1} isBasedOn={2} sharing={3} mode={4} categoryId={5} -> {6}")
  @CsvSource(nullValues = "-", value = {
      "kidney, t1,  t2, shared-with-me,        special-folders, c1, SEARCH_ID",
      "-,      -,   -,  -,                     -,               -,  VIEW_ALL",
      "'',     '',  '', '',                    '',              '', VIEW_ALL",
      "*,      -,   -,  -,                     -,               -,  VIEW_ALL",
      "kidney, -,   -,  shared-with-me,        special-folders, c1, SEARCH_TERM",
      "-,      -,   t2, shared-with-everybody, special-folders, c1, SEARCH_IS_BASED_ON",
      "-,      -,   -,  shared-with-me,        special-folders, c1, VIEW_SHARED_WITH_ME",
      "-,      -,   -,  shared-with-everybody, -,               -,  VIEW_SHARED_WITH_EVERYBODY",
      "-,      -,   -,  -,                     special-folders, c1, VIEW_SPECIAL_FOLDERS",
      "-,      -,   -,  -,                     other-mode,      c1, SEARCH_CATEGORY_ID",
      "-,      -,   -,  shared-with-nobody,    other-mode,      -,  UNKNOWN"})
  void theMostSpecificParameterChoosesTheSearch(String q, String id, String isBasedOn, String sharing, String mode,
                                                String categoryId, NodeListQueryType expected) {
    assertSame(expected, NodeListQueryTypeDetector.detect(Optional.ofNullable(q), Optional.ofNullable(id),
        Optional.ofNullable(isBasedOn), Optional.ofNullable(sharing), Optional.ofNullable(mode),
        Optional.ofNullable(categoryId)));
  }
}
