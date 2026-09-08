package org.metadatacenter.rest.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.metadatacenter.error.CedarAssertionResult;
import org.metadatacenter.error.CedarErrorPack;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.http.CedarResponseStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An assertion failure is about the request. The type says so once, so no site has to remember
 * {@code badRequest()} and the ones that forgot stop answering 500 to a caller's typo.
 */
class CedarAssertionExceptionTest {

  @Test
  @DisplayName("A message-only or cause-only assertion is a 400 without any chaining")
  void bareConstructorsDecideOnBadRequest() {
    for (CedarException e : new CedarException[]{
        new CedarAssertionException("field_names and summary=true exclude each other"),
        new CedarAssertionException(new IllegalArgumentException("not a number")),
        new CedarAssertionException("the body is not a group request", new IllegalArgumentException("x"))}) {
      assertEquals(CedarResponseStatus.BAD_REQUEST, e.getErrorPack().getStatus());
      assertTrue(e.getErrorPack().hasResolvedStatus());
    }
  }

  @Test
  @DisplayName("A status the assertion pipeline decided is kept")
  void resultStatusIsKept() {
    CedarAssertionResult forbidden = new CedarAssertionResult("not yours").forbidden();
    assertEquals(CedarResponseStatus.FORBIDDEN,
        new CedarAssertionException(forbidden).getErrorPack().getStatus());
    assertEquals(CedarResponseStatus.FORBIDDEN,
        new CedarAssertionException(forbidden, null).getErrorPack().getStatus());
    CedarErrorPack notFound = new CedarErrorPack().status(CedarResponseStatus.NOT_FOUND).message("gone");
    assertEquals(CedarResponseStatus.NOT_FOUND,
        new CedarAssertionException(notFound).getErrorPack().getStatus());
  }

  @Test
  @DisplayName("Chaining badRequest() on top is harmless")
  void explicitBadRequestStillWorks() {
    assertEquals(CedarResponseStatus.BAD_REQUEST,
        new CedarAssertionException("a bad limit").badRequest().getErrorPack().getStatus());
  }
}
