package org.metadatacenter.server.neo4j.proxy;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Value;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractNeo4JProxyTest {

  @Test
  void healthyConnectionEstablishmentHasHeadroomBeyondTheFastOutageTimeout() {
    assertEquals(5_000, AbstractNeo4JProxy.testConnectionTimeoutMillis(1_000));
  }

  @Test
  void aLargerConfiguredTimeoutIsPreserved() {
    assertEquals(10_000, AbstractNeo4JProxy.testConnectionTimeoutMillis(10_000));
  }

  @Test
  void aNullRevisionRowMeansTheLockedNodeWasDeleted() {
    Result result = mock(Result.class);
    Record record = mock(Record.class);
    Value revision = mock(Value.class);
    when(result.hasNext()).thenReturn(true);
    when(result.next()).thenReturn(record);
    when(record.get("revision")).thenReturn(revision);
    when(revision.isNull()).thenReturn(true);

    assertTrue(AbstractNeo4JProxy.readLockedRevision(result).isEmpty());
    verify(revision, never()).asLong();
  }

  @Test
  void anEmptyLockResultMeansTheNodeDoesNotExist() {
    Result result = mock(Result.class);
    when(result.hasNext()).thenReturn(false);

    assertTrue(AbstractNeo4JProxy.readLockedRevision(result).isEmpty());
    verify(result, never()).next();
  }

  @Test
  void aPresentLockRevisionIsReturned() {
    Result result = mock(Result.class);
    Record record = mock(Record.class);
    Value revision = mock(Value.class);
    when(result.hasNext()).thenReturn(true);
    when(result.next()).thenReturn(record);
    when(record.get("revision")).thenReturn(revision);
    when(revision.isNull()).thenReturn(false);
    when(revision.asLong()).thenReturn(7L);

    assertEquals(7L, AbstractNeo4JProxy.readLockedRevision(result).orElseThrow());
  }

  @Test
  void queryLogParametersRetainNamesButNotValues() {
    Map<String, Object> redacted = AbstractNeo4JProxy.redactedParameterMap(Map.of(
        "email", "person@example.org",
        "title", "Sensitive study title"));

    assertEquals(Map.of("email", "<redacted>", "title", "<redacted>"), redacted);
  }
}
