package org.metadatacenter.util.http;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.Test;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.request.ModifiedDateRange;
import static org.junit.jupiter.api.Assertions.*;

class ModifiedDateQueryTest {
  @Test void acceptsOpenRangesAndRejectsMalformedReversedAndRepeatedBounds() throws Exception {
    var params = new MultivaluedHashMap<String, String>();
    assertEquals(ModifiedDateRange.ALL, ModifiedDateQuery.parse(params));
    params.putSingle("modified_after", "100");
    assertEquals(new ModifiedDateRange(100L, null), ModifiedDateQuery.parse(params));
    params.putSingle("modified_before", "101");
    var range = ModifiedDateQuery.parse(params);
    assertTrue(range.contains(100));
    assertFalse(range.contains(101));
    params.putSingle("modified_before", "100");
    assertThrows(CedarException.class, () -> ModifiedDateQuery.parse(params));
    params.putSingle("modified_before", "bad");
    assertThrows(CedarException.class, () -> ModifiedDateQuery.parse(params));
    params.putSingle("modified_before", "200");
    params.add("modified_after", "120");
    assertThrows(CedarException.class, () -> ModifiedDateQuery.parse(params));
  }
  @Test void continuationIsBoundToItsDateRange() {
    var types = java.util.List.of("template");
    var sort = java.util.List.of("name");
    String old = SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort);
    assertEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort, ModifiedDateRange.ALL));
    assertNotEquals(old, SearchContinuation.fingerprint("*", null, types, "all", "all", null, sort, new ModifiedDateRange(100L, null)));
  }
}
