package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.rest.exception.CedarAssertionException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchContinuationTest {

  private static final String PIT = "pit-abc";
  private static final String USER = "https://metadatacenter.org/users/1";
  private static final Object[] AFTER = {1.0d, "https://repo.metadatacenter.org/templates/7"};

  private static String fingerprint() {
    return SearchContinuation.fingerprint("kidney", null, List.of("template"), "latest", "bibo:published", null,
        List.of("name"));
  }

  @Test
  void aTokenCarriesThePositionBackUnchanged() throws Exception {
    String token = SearchContinuation.of(PIT, AFTER, USER, fingerprint(), 500, 4000).encode();

    SearchContinuation decoded = SearchContinuation.decode(token, USER, fingerprint());

    assertEquals(PIT, decoded.getPointInTimeId());
    assertArrayEquals(AFTER, decoded.getSearchAfterValues());
    assertEquals(500, decoded.getRowsSeen());
    assertEquals(4000, decoded.getTotalCount());
    assertEquals(USER, decoded.getUserId());
  }

  @Test
  void aTokenTravelsInAQueryParameterWithoutEscaping() {
    String token = SearchContinuation.of(PIT, AFTER, USER, fingerprint(), 0, 1).encode();

    // Base64url without padding, so a caller can paste it into a URL as it stands.
    assertTrue(token.matches("[A-Za-z0-9_-]+"), token);
  }

  @Test
  void startIsNotAToken() {
    assertTrue(SearchContinuation.isStart("start"));
    assertFalse(SearchContinuation.isStart(SearchContinuation.of(PIT, AFTER, USER, fingerprint(), 0, 1).encode()));
  }

  @Test
  void aTokenIsRefusedForAnotherUser() {
    String token = SearchContinuation.of(PIT, AFTER, USER, fingerprint(), 0, 1).encode();

    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> SearchContinuation.decode(token, "https://metadatacenter.org/users/2", fingerprint()));

    assertEquals(CedarResponseStatus.BAD_REQUEST, error.getErrorPack().getStatus());
    assertTrue(error.getMessage().contains("another user"), error.getMessage());
  }

  @Test
  void aTokenIsRefusedWhenTheSearchUnderneathItChanged() {
    String token = SearchContinuation.of(PIT, AFTER, USER, fingerprint(), 0, 1).encode();
    String otherSort = SearchContinuation.fingerprint("kidney", null, List.of("template"), "latest", "bibo:published",
        null, List.of("createdOnTS"));

    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> SearchContinuation.decode(token, USER, otherSort));

    assertEquals(CedarResponseStatus.BAD_REQUEST, error.getErrorPack().getStatus());
    assertTrue(error.getMessage().contains("different search"), error.getMessage());
  }

  @Test
  void theLimitIsNotPartOfTheSearchATokenBelongsTo() {
    // A caller may take the rest of a walk in bigger or smaller pages: the position does not depend on
    // how it was reached, only on the order it is expressed in.
    assertEquals(fingerprint(), fingerprint());
  }

  @Test
  void aTokenThatIsNotOursIsRefusedRatherThanRead() {
    for (String bad : List.of("not base64 at all!", "", "c29tZXRoaW5nIGVsc2U")) {
      CedarAssertionException error = assertThrows(CedarAssertionException.class,
          () -> SearchContinuation.decode(bad, USER, fingerprint()), bad);
      assertEquals(CedarResponseStatus.BAD_REQUEST, error.getErrorPack().getStatus());
    }
  }

  @Test
  void aTokenFromAnotherVersionOfTheServerIsRefused() {
    String token = new SearchContinuation(99, PIT, List.of(AFTER), USER, fingerprint(), 0, 1).encode();

    CedarAssertionException error = assertThrows(CedarAssertionException.class,
        () -> SearchContinuation.decode(token, USER, fingerprint()));

    assertTrue(error.getMessage().contains("another version"), error.getMessage());
  }

  @Test
  void aTokenWithNoPositionToResumeFromIsRefused() {
    String noAfter = new SearchContinuation(1, PIT, List.of(), USER, fingerprint(), 0, 1).encode();
    String noPointInTime = new SearchContinuation(1, "", List.of(AFTER), USER, fingerprint(), 0, 1).encode();

    for (String token : List.of(noAfter, noPointInTime)) {
      CedarAssertionException error = assertThrows(CedarAssertionException.class,
          () -> SearchContinuation.decode(token, USER, fingerprint()));
      assertTrue(error.getMessage().contains("position"), error.getMessage());
    }
  }
}
