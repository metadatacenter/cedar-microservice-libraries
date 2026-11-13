package org.metadatacenter.util.http;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.request.ModifiedDateRange;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModifiedDateQueryTest {
  /** 13 November 2026, 12:00 UTC. */
  private static final long NOON = 1_794_571_200_000L;

  @Test void acceptsOpenAndClosedRanges() throws Exception {
    var params = new MultivaluedHashMap<String, String>();
    assertEquals(ModifiedDateRange.ALL, ModifiedDateQuery.parse(params));
    params.putSingle("modified_after", String.valueOf(NOON));
    assertEquals(new ModifiedDateRange(NOON, null), ModifiedDateQuery.parse(params));
    params.putSingle("modified_before", String.valueOf(NOON + 1));
    var range = ModifiedDateQuery.parse(params);
    assertTrue(range.contains(NOON));
    assertFalse(range.contains(NOON + 1));
  }

  @Test void acceptsTheSmallestBoundAndRefusesOneMillisecondLess() throws Exception {
    var params = new MultivaluedHashMap<String, String>();
    params.putSingle("modified_before", String.valueOf(ModifiedDateQuery.SMALLEST_BOUND));
    assertEquals(new ModifiedDateRange(null, ModifiedDateQuery.SMALLEST_BOUND), ModifiedDateQuery.parse(params));
    params.putSingle("modified_before", String.valueOf(ModifiedDateQuery.SMALLEST_BOUND - 1));
    assertRefused(params, Set.of("modified_before"), "falls before March 1973");
  }

  @Test void refusesATimeInSecondsAndSaysSo() {
    var params = new MultivaluedHashMap<String, String>();
    params.putSingle("modified_after", String.valueOf(NOON / 1000));
    CedarException refusal = assertRefused(params, Set.of("modified_after"), "If it is a time in seconds");
    assertEquals(String.valueOf(NOON / 1000), refusal.getErrorPack().getParameters().get("modified_after"));
  }

  @Test void refusesANegativeTime() {
    var params = new MultivaluedHashMap<String, String>();
    params.putSingle("modified_before", "-1");
    assertRefused(params, Set.of("modified_before"), "falls before March 1973");
  }

  @Test void refusesAValueThatIsNotANumber() {
    var params = new MultivaluedHashMap<String, String>();
    params.putSingle("modified_before", "yesterday");
    assertRefused(params, Set.of("modified_before"), "'yesterday' is not one");
    params.putSingle("modified_before", "");
    assertRefused(params, Set.of("modified_before"), "'' is not one");
    params.putSingle("modified_before", "99999999999999999999");
    assertRefused(params, Set.of("modified_before"), "is not one");
  }

  @Test void refusesABoundGivenTwiceEvenWhenTheValuesAgree() {
    var params = new MultivaluedHashMap<String, String>();
    params.put("modified_after", List.of(String.valueOf(NOON), String.valueOf(NOON)));
    CedarException refusal = assertRefused(params, Set.of("modified_after"), "given 2 times");
    assertEquals(List.of(String.valueOf(NOON), String.valueOf(NOON)),
        refusal.getErrorPack().getParameters().get("modified_after"));
  }

  @Test void refusesAWindowThatDoesNotEndAfterItStarts() {
    var params = new MultivaluedHashMap<String, String>();
    params.putSingle("modified_after", String.valueOf(NOON));
    params.putSingle("modified_before", String.valueOf(NOON));
    assertRefused(params, Set.of("modified_after", "modified_before"), "must be later than modified_after");
    params.putSingle("modified_before", String.valueOf(NOON - 1));
    assertRefused(params, Set.of("modified_after", "modified_before"), "must be later than modified_after");
  }

  @Test void continuationIsBoundToItsDateRange() {
    var types = List.of("template");
    var sort = List.of("name");
    String old = SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort);
    assertEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort, ModifiedDateRange.ALL));
    assertNotEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort,
        new ModifiedDateRange(NOON, null)));
  }

  /**
   * Asserts a 400 whose parameters are exactly the named ones and whose message contains the given text,
   * and returns it for further checks.
   */
  private static CedarException assertRefused(MultivaluedHashMap<String, String> params, Set<String> named,
                                              String messagePart) {
    CedarException refusal = assertThrows(CedarException.class, () -> ModifiedDateQuery.parse(params));
    assertEquals(400, refusal.getErrorPack().getStatus().getStatusCode(), refusal.getMessage());
    assertEquals(named, refusal.getErrorPack().getParameters().keySet(), refusal.getMessage());
    assertTrue(refusal.getErrorPack().getMessage().contains(messagePart),
        "'" + refusal.getErrorPack().getMessage() + "' does not contain '" + messagePart + "'");
    return refusal;
  }
}
