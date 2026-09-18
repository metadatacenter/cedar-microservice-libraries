package org.metadatacenter.cedar.util.dw;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.ListIndexesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What the probe reports for the index shapes a store can hold. The store is mocked here because
 * these are readings of index metadata; that the embedded and deployed stores actually carry the
 * index is proved against a real store in the artifact server's suite.
 */
public class ArtifactIdIndexProbeTest {

  private static final String DATABASE = "cedar";
  private static final String COLLECTION = "templates";

  @Test
  public void reportsAUniqueIdIndexAsPresent() {
    ArtifactIdIndexProbe.Status status = probeReturning(
        new Document("name", "_id_").append("key", new Document("_id", 1)),
        new Document("name", "@id_1").append("key", new Document("@id", 1)).append("unique", true));

    assertTrue(status.complete());
    assertEquals(List.of(COLLECTION), status.withIndex());
    assertTrue(status.describe().contains("present"));
  }

  @Test
  public void aCollectionCarryingOnlyTheDefaultIndexIsReportedWithoutOne() {
    ArtifactIdIndexProbe.Status status = probeReturning(
        new Document("name", "_id_").append("key", new Document("_id", 1)));

    assertFalse(status.complete());
    assertEquals(List.of(COLLECTION), status.withoutIndex());
    assertTrue(status.describe().contains("no unique @id index on " + COLLECTION));
  }

  /**
   * A non-unique index removes the collection scan but enforces nothing, so it is not the index
   * this probe is asked about.
   */
  @Test
  public void aNonUniqueIdIndexDoesNotCount() {
    ArtifactIdIndexProbe.Status status = probeReturning(
        new Document("name", "@id_1").append("key", new Document("@id", 1)));

    assertFalse(status.complete());
    assertEquals(List.of(COLLECTION), status.withoutIndex());
  }

  /** An unreachable store yields a reason rather than an exception, so no caller defends against one. */
  @Test
  public void anUnreadableStoreIsReportedAsUnknown()  {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    MongoCollection<Document> collection = mock(MongoCollection.class);
    when(client.getDatabase(DATABASE)).thenReturn(database);
    when(database.getCollection(anyString())).thenReturn(collection);
    when(collection.listIndexes()).thenThrow(new MongoTimeoutException("no server available"));

    ArtifactIdIndexProbe.Status status =
        new ArtifactIdIndexProbe(client, DATABASE, List.of(COLLECTION)).inspect();

    assertFalse(status.complete());
    assertTrue(status.unreadable().isPresent());
    assertTrue(status.describe().startsWith("index state unknown: MongoTimeoutException"));
  }

  private ArtifactIdIndexProbe.Status probeReturning(Document... indexes) {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    MongoCollection<Document> collection = mock(MongoCollection.class);
    ListIndexesIterable<Document> iterable = mock(ListIndexesIterable.class);
    when(client.getDatabase(DATABASE)).thenReturn(database);
    when(database.getCollection(anyString())).thenReturn(collection);
    when(collection.listIndexes()).thenReturn(iterable);
    when(iterable.into(any())).thenReturn(new ArrayList<>(List.of(indexes)));

    return new ArtifactIdIndexProbe(client, DATABASE, List.of(COLLECTION)).inspect();
  }
}
