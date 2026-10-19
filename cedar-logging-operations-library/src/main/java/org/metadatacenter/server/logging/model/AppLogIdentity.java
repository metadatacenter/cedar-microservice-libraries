package org.metadatacenter.server.logging.model;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.metadatacenter.util.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;

/** Stable identities for request phases and individual query executions, including queued legacy messages. */
public final class AppLogIdentity {
  private static final ObjectMapper CANONICAL = JsonMapper.STRICT_MAPPER.copy()
      .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  private AppLogIdentity() { }

  public static String key(AppLogMessage message) {
    if (message.getType() != AppLogType.CYPHER_QUERY) {
      if (message.getLocalRequestId() == null || message.getLocalRequestId().isBlank()) {
        throw new IllegalArgumentException("A request log fragment requires a local request ID");
      }
      // One receipt serializes all phases and survives pruning as the request's tombstone.
      return hash("request:" + message.getLocalRequestId());
    }
    if (message.getEventId() != null) return hash("event:" + message.getEventId());
    // Old producers have no eventId. Include every original field and canonicalize map key order;
    // do not generate a new UUID at deserialization, which would turn each replay into a new event.
    Map<String, Object> fields = new TreeMap<>();
    fields.put("globalRequestId", message.getGlobalRequestId());
    fields.put("localRequestId", message.getLocalRequestId());
    fields.put("systemComponent", message.getSystemComponent());
    fields.put("type", message.getType()); fields.put("subType", message.getSubType());
    fields.put("logTime", message.getLogTime()); fields.put("startTime", message.getStartTime());
    fields.put("endTime", message.getEndTime()); fields.put("duration", message.getDuration());
    fields.put("parameters", message.getParameters());
    try { return hash("legacy:" + CANONICAL.writeValueAsString(fields)); }
    catch (JsonProcessingException e) { throw new IllegalArgumentException("Cannot identify log event", e); }
  }

  private static String hash(String value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
    catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
  }
}
