package org.metadatacenter.util.http;

import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.request.ModifiedDateRange;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ModifiedDateQueryTest {
  /** 13 November 2026, 12:00 UTC. */
  private static final long NOON = 1_794_571_200_000L;
  private static final String NOON_INSTANT = "2026-11-13T12:00:00Z";

  @Test void acceptsOpenAndClosedRanges() throws Exception {
    assertEquals(ModifiedDateRange.ALL, parse(null, null));
    assertEquals(ModifiedDateRange.ALL, parse(List.of(), List.of()));
    assertEquals(new ModifiedDateRange(NOON, null), parse(List.of(NOON_INSTANT), null));
    var range = parse(List.of(NOON_INSTANT), List.of("2026-11-13T12:00:00.001Z"));
    assertTrue(range.contains(NOON));
    assertFalse(range.contains(NOON + 1));
  }

  @Test void acceptsAnOffsetAndReadsItAsTheSameInstant() throws Exception {
    assertEquals(new ModifiedDateRange(NOON, null), parse(List.of("2026-11-13T13:00:00+01:00"), null));
    assertEquals(new ModifiedDateRange(null, NOON), parse(null, List.of("2026-11-13T07:00:00-05:00")));
  }

  @Test void roundsAFinerBoundUpToTheNextMillisecond() throws Exception {
    assertEquals(new ModifiedDateRange(NOON + 1, null), parse(List.of("2026-11-13T12:00:00.000000001Z"), null));
    assertEquals(new ModifiedDateRange(null, NOON + 1), parse(null, List.of("2026-11-13T12:00:00.0005Z")));
  }

  @Test void formatsABoundAsAnInstantItReadsBack() throws Exception {
    assertEquals(NOON_INSTANT, ModifiedDateQuery.format(NOON));
    assertEquals(new ModifiedDateRange(NOON + 7, null),
        parse(List.of(ModifiedDateQuery.format(NOON + 7)), null));
  }

  @Test void refusesATimeInEpochMillisecondsAndSaysSo() {
    CedarException refusal = assertRefused(List.of(String.valueOf(NOON)), null, Set.of("modified_after"),
        "is a number");
    assertEquals(String.valueOf(NOON), refusal.getErrorPack().getParameters().get("modified_after"));
    assertRefused(null, List.of(String.valueOf(NOON / 1000)), Set.of("modified_before"), "is a number");
    assertRefused(null, List.of("-1"), Set.of("modified_before"), "is a number");
  }

  @Test void refusesAValueThatIsNotAnInstant() {
    assertRefused(null, List.of("yesterday"), Set.of("modified_before"), "'yesterday' is not one");
    assertRefused(null, List.of(""), Set.of("modified_before"), "'' is not one");
    assertRefused(null, List.of("2026-11-13"), Set.of("modified_before"), "is not one");
    assertRefused(null, List.of("2026-11-13T12:00:00"), Set.of("modified_before"), "is not one");
    // An unencoded + arrives as a space.
    assertRefused(null, List.of("2026-11-13T13:00:00 01:00"), Set.of("modified_before"), "is not one");
  }

  @Test void refusesAnInstantTooFarAwayToCompare() {
    assertRefused(List.of("+1000000000-01-01T00:00:00Z"), null, Set.of("modified_after"), "too far");
  }

  @Test void refusesABoundGivenTwiceEvenWhenTheValuesAgree() {
    CedarException refusal = assertRefused(List.of(NOON_INSTANT, NOON_INSTANT), null, Set.of("modified_after"),
        "given 2 times");
    assertEquals(List.of(NOON_INSTANT, NOON_INSTANT), refusal.getErrorPack().getParameters().get("modified_after"));
  }

  @Test void refusesAWindowThatDoesNotEndAfterItStarts() {
    assertRefused(List.of(NOON_INSTANT), List.of(NOON_INSTANT), Set.of("modified_after", "modified_before"),
        "must be later than modified_after");
    assertRefused(List.of(NOON_INSTANT), List.of("2026-11-13T11:59:59.999Z"),
        Set.of("modified_after", "modified_before"), "must be later than modified_after");
  }

  @Test void continuationIsBoundToItsDateRange() {
    var types = List.of("template");
    var sort = List.of("name");
    String old = SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort);
    assertEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort, ModifiedDateRange.ALL));
    assertNotEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort,
        new ModifiedDateRange(NOON, null)));
  }

  private static ModifiedDateRange parse(List<String> after, List<String> before) throws CedarException {
    return ModifiedDateQuery.parse(after, before);
  }

  /**
   * Asserts a 400 whose parameters are exactly the named ones and whose message contains the given text,
   * and returns it for further checks.
   */
  private static CedarException assertRefused(List<String> after, List<String> before, Set<String> named,
                                              String messagePart) {
    CedarException refusal = assertThrows(CedarException.class, () -> ModifiedDateQuery.parse(after, before));
    assertEquals(400, refusal.getErrorPack().getStatus().getStatusCode(), refusal.getMessage());
    assertEquals(named, refusal.getErrorPack().getParameters().keySet(), refusal.getMessage());
    assertTrue(refusal.getErrorPack().getMessage().contains(messagePart),
        "'" + refusal.getErrorPack().getMessage() + "' does not contain '" + messagePart + "'");
    return refusal;
  }
}
