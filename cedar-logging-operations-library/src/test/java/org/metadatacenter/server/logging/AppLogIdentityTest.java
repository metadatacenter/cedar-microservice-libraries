package org.metadatacenter.server.logging;

import org.junit.jupiter.api.Test;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.model.*;
import org.metadatacenter.util.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.*;

class AppLogIdentityTest {
  @Test void newExecutionIdentitySurvivesSerialization() throws Exception {
    AppLogMessage message = new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.CYPHER_QUERY,
        AppLogSubType.FULL, "global", "local");
    AppLogMessage read = JsonMapper.STRICT_MAPPER.readValue(JsonMapper.STRICT_MAPPER.writeValueAsString(message), AppLogMessage.class);
    assertNotNull(read.getEventId());
    assertEquals(message.getEventId(), read.getEventId());
    assertEquals(AppLogIdentity.key(message), AppLogIdentity.key(read));
  }

  @Test void legacyIdentityIgnoresMapKeyOrderButIncludesParameterValues() throws Exception {
    // Use the enum's serialized values rather than a parallel spelling of the wire format.
    var base = JsonMapper.STRICT_MAPPER.valueToTree(new AppLogMessage(SystemComponent.SERVER_RESOURCE,
        AppLogType.CYPHER_QUERY, AppLogSubType.FULL, "global", "local"));
    ((com.fasterxml.jackson.databind.node.ObjectNode) base).remove("eventId");
    ((com.fasterxml.jackson.databind.node.ObjectNode) base).set("parameters", JsonMapper.STRICT_MAPPER.readTree("{\"queryParameters\":{\"a\":1,\"b\":2}}"));
    AppLogMessage first = JsonMapper.STRICT_MAPPER.treeToValue(base, AppLogMessage.class);
    ((com.fasterxml.jackson.databind.node.ObjectNode) base).set("parameters", JsonMapper.STRICT_MAPPER.readTree("{\"queryParameters\":{\"b\":2,\"a\":1}}"));
    AppLogMessage reordered = JsonMapper.STRICT_MAPPER.treeToValue(base, AppLogMessage.class);
    assertNull(reordered.getEventId());
    assertEquals(AppLogIdentity.key(first), AppLogIdentity.key(reordered));
    reordered.param(AppLogParam.OPERATION, "different");
    assertNotEquals(AppLogIdentity.key(first), AppLogIdentity.key(reordered));
  }

  @Test void allRequestPhasesShareOneLockAndDifferentRequestsDoNot() {
    AppLogMessage start = new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.REQUEST_FILTER,
        AppLogSubType.START, "global", "local");
    AppLogMessage end = new AppLogMessage(SystemComponent.SERVER_RESOURCE, AppLogType.REQUEST_FILTER,
        AppLogSubType.END, "global", "local");
    assertEquals(AppLogIdentity.key(start), AppLogIdentity.key(end));
    assertNotEquals(AppLogIdentity.key(start), AppLogIdentity.key(new AppLogMessage(SystemComponent.SERVER_RESOURCE,
        AppLogType.REQUEST_FILTER, AppLogSubType.START, "global", "another")));
    assertThrows(IllegalArgumentException.class, () -> AppLogIdentity.key(new AppLogMessage(SystemComponent.SERVER_RESOURCE,
        AppLogType.REQUEST_FILTER, AppLogSubType.START, "global", null)));
  }
}
