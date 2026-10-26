package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoSocketReadException;
import com.mongodb.MongoWriteException;
import com.mongodb.ServerAddress;
import com.mongodb.WriteError;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactRevisionConflictException;
import org.metadatacenter.server.dao.TemplateReferencedException;
import org.metadatacenter.util.json.JsonMapper;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The order of operations that keeps a template from being deleted or restructured while an instance
 * that references it is being written. The artifact server proves the protocol against a real store
 * with paused writers. These pin it in the library that owns it: a writer reserves before it reads the
 * template's fence, a template mutation fences before it counts reservations and then instances, only
 * an uncertain write leaves its reservation behind, and a reservation is reaped only when its write
 * can no longer commit.
 */
class TemplateReferenceGuardTest {

  private static final String TEMPLATE_ID = "https://repo.metadatacenter.org/templates/t1";
  private static final String INSTANCE_ID = "https://repo.metadatacenter.org/template-instances/i1";

  @SuppressWarnings("unchecked")
  private final MongoCollection<Document> templates = mock(MongoCollection.class);
  @SuppressWarnings("unchecked")
  private final MongoCollection<Document> instances = mock(MongoCollection.class);
  @SuppressWarnings("unchecked")
  private final MongoCollection<Document> reservations = mock(MongoCollection.class);
  private final TemplateDaoMongoDB dao = mock(TemplateDaoMongoDB.class);
  private final TemplateReferenceGuard guard;

  TemplateReferenceGuardTest() {
    MongoClient client = mock(MongoClient.class);
    MongoDatabase database = mock(MongoDatabase.class);
    when(client.getDatabase("cedar")).thenReturn(database);
    when(database.getCollection("templates")).thenReturn(templates);
    when(database.getCollection("instances")).thenReturn(instances);
    when(database.getCollection("templates_reference_reservations")).thenReturn(reservations);
    guard = new TemplateReferenceGuard(client, "cedar", "templates", "instances");
  }

  @Test
  void aWriterReservesBeforeItReadsTheTemplateFenceAndReleasesAfterItsWrite() throws Exception {
    templateIsWritable(true);
    JsonNode stored = JsonMapper.STRICT_MAPPER.createObjectNode().put("@id", INSTANCE_ID);
    TemplateReferenceGuard.InstanceWrite<JsonNode> write = writeReturning(stored);

    assertSame(stored, guard.write(instance(), 3, write));

    InOrder order = inOrder(reservations, templates, write);
    ArgumentCaptor<Document> reservation = ArgumentCaptor.forClass(Document.class);
    order.verify(reservations).insertOne(reservation.capture());
    order.verify(templates).find(any(Bson.class));
    order.verify(write).run();
    order.verify(reservations).deleteOne(any(Bson.class));
    assertEquals(TEMPLATE_ID, reservation.getValue().getString("templateId"));
    assertEquals(INSTANCE_ID, reservation.getValue().getString("instanceId"));
    assertEquals(3L, reservation.getValue().get("expectedRevision", Number.class).longValue());
    assertEquals(List.of(reservation.getValue().getString("_id")), deletedReservationIds());
  }

  /**
   * The template the writer must still see: fenced by no mutation, and at the revision validation
   * read when one is given. Revision 0 is a template stored before revisions existed.
   */
  @ParameterizedTest
  @MethodSource("templateRevisions")
  void theFenceCheckRequiresNoDeletionTokenAndTheValidatedRevision(Long templateRevision, String revisionClause)
      throws Exception {
    templateIsWritable(true);

    guard.write(instance(), -1, templateRevision, writeReturning(null));

    ArgumentCaptor<Bson> filter = ArgumentCaptor.forClass(Bson.class);
    verify(templates).find(filter.capture());
    String rendered = render(filter.getValue()).toJson();
    assertTrue(rendered.contains("\"@id\": \"" + TEMPLATE_ID + "\""), rendered);
    assertTrue(rendered.contains("\"_cedarDeletionToken\": {\"$exists\": false}"), rendered);
    if (revisionClause == null) {
      assertFalse(rendered.contains("_cedarRevision"), rendered);
    } else {
      assertTrue(rendered.contains(revisionClause), rendered);
    }
  }

  private static Stream<Arguments> templateRevisions() {
    return Stream.of(
        Arguments.of(null, null),
        Arguments.of(0L, "\"_cedarRevision\": {\"$exists\": false}"),
        Arguments.of(7L, "\"_cedarRevision\": 7"));
  }

  @Test
  void aFencedOrChangedTemplateRefusesTheWriteWithoutRunningIt() throws Exception {
    templateIsWritable(false);
    TemplateReferenceGuard.InstanceWrite<JsonNode> write = writeReturning(null);

    assertThrows(ArtifactRevisionConflictException.class, () -> guard.write(instance(), -1, 4L, write));

    verify(write, never()).run();
    assertEquals(1, deletedReservationIds().size(), "a refused write has a known outcome and leaves no reservation");
  }

  /**
   * A write whose acknowledgement was lost may still commit, so its reservation has to keep blocking
   * deletion. A write the server rejected cannot commit, and keeping its reservation would block the
   * template's deletion for no reason.
   */
  @ParameterizedTest
  @ValueSource(strings = {"lost acknowledgement", "lost socket", "write rejected", "instance missing"})
  void onlyAWriteThatMightStillCommitKeepsItsReservation(String failure) throws Exception {
    templateIsWritable(true);
    Exception thrown = switch (failure) {
      case "lost acknowledgement" -> new IOException("acknowledgement lost");
      case "lost socket" -> new MongoSocketReadException("socket closed", new ServerAddress());
      case "write rejected" -> new MongoWriteException(
          new WriteError(121, "Document failed validation", new BsonDocument()), new ServerAddress());
      default -> new ArtifactServerResourceNotFoundException();
    };
    boolean uncertain = failure.startsWith("lost");

    Exception observed = assertThrows(Exception.class, () -> guard.write(instance(), 2, () -> {
      if (thrown instanceof IOException io) throw io;
      if (thrown instanceof ArtifactServerResourceNotFoundException notFound) throw notFound;
      throw (RuntimeException) thrown;
    }));

    assertSame(thrown, observed);
    assertEquals(uncertain ? 0 : 1, deletedReservationIds().size());
  }

  @Test
  void aTemplateIsFencedBeforeReservationsAndThenInstancesAreCounted() throws Exception {
    fenceMatches(1);
    noReservations();

    guard.delete(TEMPLATE_ID, 5, dao);

    InOrder order = inOrder(templates, reservations, instances, dao);
    ArgumentCaptor<Bson> fence = ArgumentCaptor.forClass(Bson.class);
    order.verify(templates).updateOne(any(Bson.class), fence.capture());
    order.verify(reservations).countDocuments(any(Bson.class));
    order.verify(instances).countDocuments(any(Bson.class));
    ArgumentCaptor<Bson> condition = ArgumentCaptor.forClass(Bson.class);
    order.verify(dao).deleteMatching(org.mockito.ArgumentMatchers.eq(TEMPLATE_ID), org.mockito.ArgumentMatchers.eq(5L),
        condition.capture());
    ArgumentCaptor<Bson> release = ArgumentCaptor.forClass(Bson.class);
    order.verify(templates).updateOne(release.capture(), any(Bson.class));
    String token = render(fence.getValue()).getDocument("$set").getString(TemplateReferenceGuard.DELETION_TOKEN).getValue();
    assertEquals(token, render(condition.getValue()).getString(TemplateReferenceGuard.DELETION_TOKEN).getValue(),
        "the deletion must be conditional on this request's own fence");
    assertTrue(render(release.getValue()).toJson().contains(token),
        "the fence is removed only where this request's token still stands");
  }

  /**
   * A template that stored instances or an instance write in flight reference is refused as referenced,
   * which a caller can tell from a race: the template's own state refuses the write, and a retry against
   * its current revision is refused again.
   */
  @ParameterizedTest
  @MethodSource("references")
  void aReferencedTemplateIsRefusedAndItsFenceIsLifted(long reserved, long stored) throws Exception {
    fenceMatches(1);
    noReservations();
    when(reservations.countDocuments(any(Bson.class))).thenReturn(reserved);
    when(instances.countDocuments(any(Bson.class))).thenReturn(stored);

    assertThrows(TemplateReferencedException.class,
        () -> guard.update(TEMPLATE_ID, JsonMapper.STRICT_MAPPER.createObjectNode(), 5, dao));
    assertThrows(TemplateReferencedException.class, () -> guard.delete(TEMPLATE_ID, 5, dao));

    verify(dao, never()).updateMatching(anyString(), any(), anyLong(), any());
    verify(dao, never()).deleteMatching(anyString(), anyLong(), any());
    verify(templates, times(4)).updateOne(any(Bson.class), any(Bson.class));
  }

  private static Stream<Arguments> references() {
    return Stream.of(Arguments.of(1L, 0L), Arguments.of(0L, 2L));
  }

  @Test
  void aStaleRevisionIsRefusedBeforeAnythingIsCounted() throws Exception {
    fenceMatches(0);

    assertRace(assertThrows(ArtifactRevisionConflictException.class, () -> guard.delete(TEMPLATE_ID, 5, dao)));

    verify(reservations, never()).countDocuments(any(Bson.class));
    verify(instances, never()).countDocuments(any(Bson.class));
    verify(dao, never()).deleteMatching(anyString(), anyLong(), any());
    verify(templates, times(1)).updateOne(any(Bson.class), any(Bson.class));
  }

  /**
   * Another guarded mutation can replace this request's fence after the references were counted, and the
   * write conditional on that fence then matches nothing. That is a race the other writer won, not a
   * reference, and it is reported as the race it is.
   */
  @Test
  void aFenceAnotherMutationTookOverIsARaceRatherThanAReference() throws Exception {
    fenceMatches(1);
    noReservations();
    when(dao.updateMatching(anyString(), any(), anyLong(), any()))
        .thenThrow(new ArtifactRevisionConflictException(TEMPLATE_ID, 5));
    org.mockito.Mockito.doThrow(new ArtifactRevisionConflictException(TEMPLATE_ID, 5))
        .when(dao).deleteMatching(anyString(), anyLong(), any());

    assertRace(assertThrows(ArtifactRevisionConflictException.class,
        () -> guard.update(TEMPLATE_ID, JsonMapper.STRICT_MAPPER.createObjectNode(), 5, dao)));
    assertRace(assertThrows(ArtifactRevisionConflictException.class, () -> guard.delete(TEMPLATE_ID, 5, dao)));
  }

  private static void assertRace(ArtifactRevisionConflictException refusal) {
    assertFalse(refusal instanceof TemplateReferencedException, "a race was reported as a reference: " + refusal);
  }

  /**
   * A reservation another process left behind blocks deletion until its write is known to be unable
   * to commit: it settled, or the instance it would update has gone or moved past the revision it
   * expected. An insert's outcome cannot be inferred from the store, so it stays.
   */
  @Test
  void reapingRemovesOnlyReservationsWhoseWriteCanNoLongerCommit() throws Exception {
    fenceMatches(1);
    givenReservations(List.of(
        reservation("settled", "i-settled", 3).append("settled", true),
        reservation("moved-on", "i-moved", 3),
        reservation("gone", "i-gone", 3),
        reservation("still-possible", "i-current", 3),
        reservation("unversioned", "i-unversioned", 0),
        reservation("insert", "i-new", -1)),
        Map.of("i-settled", instanceAt(3L), "i-moved", instanceAt(4L), "i-current", instanceAt(3L),
            "i-unversioned", instanceAt(null)));
    when(reservations.countDocuments(any(Bson.class))).thenReturn(3L);

    assertThrows(ArtifactRevisionConflictException.class, () -> guard.delete(TEMPLATE_ID, 5, dao));

    assertEquals(List.of("settled", "moved-on", "gone"), deletedReservationIds());
  }

  private void templateIsWritable(boolean writable) {
    @SuppressWarnings("unchecked")
    FindIterable<Document> found = mock(FindIterable.class);
    when(found.first()).thenReturn(writable ? new Document("@id", TEMPLATE_ID) : null);
    when(templates.find(any(Bson.class))).thenReturn(found);
  }

  private void fenceMatches(long matched) {
    when(templates.updateOne(any(Bson.class), any(Bson.class))).thenReturn(UpdateResult.acknowledged(matched, matched, null));
  }

  private void noReservations() {
    givenReservations(List.of(), Map.of());
  }

  @SuppressWarnings("unchecked")
  private void givenReservations(List<Document> pending, Map<String, Document> storedInstances) {
    FindIterable<Document> listed = mock(FindIterable.class);
    MongoCursor<Document> cursor = mock(MongoCursor.class);
    Iterator<Document> remaining = pending.iterator();
    when(cursor.hasNext()).thenAnswer(invocation -> remaining.hasNext());
    when(cursor.next()).thenAnswer(invocation -> remaining.next());
    when(listed.iterator()).thenReturn(cursor);
    when(reservations.find(any(Bson.class))).thenReturn(listed);
    when(instances.find(any(Bson.class))).thenAnswer(invocation -> {
      FindIterable<Document> one = mock(FindIterable.class);
      String id = render(invocation.getArgument(0)).getString("@id").getValue();
      when(one.first()).thenReturn(storedInstances.get(id));
      return one;
    });
  }

  private static Document reservation(String operation, String instanceId, long expectedRevision) {
    return new Document("_id", operation).append("templateId", TEMPLATE_ID).append("instanceId", instanceId)
        .append("expectedRevision", expectedRevision);
  }

  private static Document instanceAt(Long revision) {
    Document instance = new Document("schema:isBasedOn", TEMPLATE_ID);
    if (revision != null) {
      instance.append(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, revision);
    }
    return instance;
  }

  private List<String> deletedReservationIds() {
    ArgumentCaptor<Bson> deleted = ArgumentCaptor.forClass(Bson.class);
    verify(reservations, org.mockito.Mockito.atLeast(0)).deleteOne(deleted.capture());
    return deleted.getAllValues().stream().map(filter -> render(filter).getString("_id").getValue()).toList();
  }

  @SuppressWarnings("unchecked")
  private static TemplateReferenceGuard.InstanceWrite<JsonNode> writeReturning(JsonNode stored) throws Exception {
    TemplateReferenceGuard.InstanceWrite<JsonNode> write = mock(TemplateReferenceGuard.InstanceWrite.class);
    when(write.run()).thenReturn(stored);
    return write;
  }

  private static JsonNode instance() {
    return JsonMapper.STRICT_MAPPER.createObjectNode().put("@id", INSTANCE_ID).put("schema:isBasedOn", TEMPLATE_ID);
  }

  private static BsonDocument render(Bson bson) {
    return bson.toBsonDocument(BsonDocument.class, MongoClientSettings.getDefaultCodecRegistry());
  }
}
