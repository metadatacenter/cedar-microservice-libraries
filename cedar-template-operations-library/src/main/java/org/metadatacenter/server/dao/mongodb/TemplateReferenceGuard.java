package org.metadatacenter.server.dao.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactRevisionConflictException;

import java.io.IOException;
import java.util.UUID;

import static com.mongodb.client.model.Filters.*;
import static com.mongodb.client.model.Updates.*;

/**
 * Coordinates live instance writes with template deletion and inclusion updates across processes.
 * A writer reserves before checking the template fence; a template mutation fences before counting reservations
 * and then stored references. An uncertain write leaves its reservation for inspection, never a gap
 * in which a template mutation may orphan or invalidate it. Completed reservations can be recovered
 * using revision predicates. Validation supplies the exact template revision the writer must still see.
 */
public final class TemplateReferenceGuard {
  static final String DELETION_TOKEN = "_cedarDeletionToken";
  private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(TemplateReferenceGuard.class);
  private final MongoCollection<Document> templates;
  private final MongoCollection<Document> instances;
  private final MongoCollection<Document> reservations;

  public TemplateReferenceGuard(MongoClient client, String database, String templateCollection,
                                String instanceCollection) {
    templates = client.getDatabase(database).getCollection(templateCollection);
    instances = client.getDatabase(database).getCollection(instanceCollection);
    reservations = client.getDatabase(database).getCollection(templateCollection + "_reference_reservations");
  }

  @FunctionalInterface public interface InstanceWrite<T> {
    T run() throws IOException, ArtifactServerResourceNotFoundException;
  }

  public <T> T write(JsonNode body, long expectedRevision, InstanceWrite<T> write)
      throws IOException, ArtifactServerResourceNotFoundException {
    return write(body, expectedRevision, null, write);
  }

  public <T> T write(JsonNode body, long expectedRevision, Long templateRevision, InstanceWrite<T> write)
      throws IOException, ArtifactServerResourceNotFoundException {
    String templateId = body.path("schema:isBasedOn").asText();
    String instanceId = body.path("@id").asText();
    String operation = UUID.randomUUID().toString();
    reservations.insertOne(new Document("_id", operation).append("templateId", templateId)
        .append("instanceId", instanceId).append("expectedRevision", expectedRevision));
    boolean settled = false;
    try {
      var templateVersion = templateRevision == null ? new Document()
          : templateRevision == 0 ? exists(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, false)
          : eq(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, templateRevision);
      if (templates.find(and(eq("@id", templateId), exists(DELETION_TOKEN, false), templateVersion)).first() == null) {
        settled = true;
        throw new ArtifactRevisionConflictException(templateId);
      }
      T stored = write.run();
      settled = true;
      return stored;
    } catch (ArtifactRevisionConflictException | ArtifactServerResourceNotFoundException
             | com.mongodb.MongoWriteException e) {
      // A server-side write rejection is definitive (for example document validation or duplicate
      // identity). A network/acknowledgement failure is different and keeps the reservation below.
      settled = true;
      throw e;
    } finally {
      if (settled) {
        try {
          reservations.updateOne(eq("_id", operation), set("settled", true));
          reservations.deleteOne(eq("_id", operation));
        } catch (RuntimeException e) {
          // The write's outcome is known. A future deletion can safely reap this reservation.
          log.error("Unable to remove completed instance reservation {} for {}", operation, instanceId, e);
        }
      }
    }
  }

  public void delete(String id, long revision, TemplateDaoMongoDB dao)
      throws IOException, ArtifactServerResourceNotFoundException {
    unreferenced(id, revision, token -> { dao.deleteMatching(id, revision, eq(DELETION_TOKEN, token)); return null; });
  }

  public JsonNode update(String id, JsonNode content, long revision, TemplateDaoMongoDB dao)
      throws IOException, ArtifactServerResourceNotFoundException {
    return unreferenced(id, revision, token -> dao.updateMatching(id, content, revision, eq(DELETION_TOKEN, token)));
  }

  @FunctionalInterface private interface FencedWrite<T> {
    T run(String token) throws IOException, ArtifactServerResourceNotFoundException;
  }

  private <T> T unreferenced(String id, long revision, FencedWrite<T> write)
      throws IOException, ArtifactServerResourceNotFoundException {
    String token = UUID.randomUUID().toString();
    var revisionFilter = revision == 0 ? exists(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, false)
        : eq(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, revision);
    if (templates.updateOne(and(eq("@id", id), revisionFilter), set(DELETION_TOKEN, token))
        .getMatchedCount() != 1) {
      throw new ArtifactRevisionConflictException(id, revision);
    }
    try {
      reapCompleted(id);
      // Read reservations first. A writer that removes its reservation has already made its
      // document visible; a new reservation cannot pass the deletion fence installed above.
      if (reservations.countDocuments(eq("templateId", id)) != 0
          || instances.countDocuments(eq("schema:isBasedOn", id)) != 0) {
        throw new ArtifactRevisionConflictException(id, revision);
      }
      return write.run(token);
    } finally {
      // A competing guarded mutation may have taken over, or a template edit may have advanced the revision.
      // Neither operation may have its fence removed by this request.
      templates.updateOne(and(eq("@id", id), eq(DELETION_TOKEN, token)), unset(DELETION_TOKEN));
    }
  }

  private void reapCompleted(String templateId) {
    try (var cursor = reservations.find(eq("templateId", templateId)).iterator()) {
      while (cursor.hasNext()) {
        Document reservation = cursor.next();
        long expected = reservation.get("expectedRevision", Number.class).longValue();
        Document current = instances.find(eq("@id", reservation.getString("instanceId"))).first();
        // Inserts cannot replace an existing identifier; updates cannot recreate a missing one.
        // A changed revision also proves that a still-running conditional write cannot commit.
        Number currentRevision = current == null ? null
            : current.get(GenericLDDaoMongoDB.INTERNAL_REVISION_FIELD, Number.class);
        boolean cannotCommit = Boolean.TRUE.equals(reservation.getBoolean("settled")) || expected >= 0
            && (current == null || (currentRevision == null ? 0L : currentRevision.longValue()) != expected);
        if (cannotCommit) reservations.deleteOne(eq("_id", reservation.getString("_id")));
      }
    }
  }
}
