package org.metadatacenter.cedar.util.dw;

import com.mongodb.client.MongoClient;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reads whether each artifact collection carries the unique index on {@code @id} that the store is
 * relied on to enforce.
 *
 * <p>No two documents in an artifact collection may share an {@code @id}, and every artifact
 * operation addresses its document by that field, so both properties rest on that index. Nothing in
 * an application creates it: natively the admin tool's {@code artifactServer-initDB} task does, and
 * in Docker the Mongo image's {@code create-indices.js} does, on a container's first boot. A store
 * that went through neither accepts a repeated identifier and answers every lookup with a
 * collection scan, which is how a production store ran for years unindexed with nothing reporting
 * it.
 *
 * <p>This probe only reads index metadata, and never creates an index. Creating one is a privileged
 * operation with a failure mode of its own - a unique build over a collection that already holds a
 * repeated identifier fails - so it belongs to a provisioning step the release owns rather than to
 * a server's startup.
 */
public final class ArtifactIdIndexProbe {

  static final String ID_FIELD = "@id";

  private final MongoClient client;
  private final String databaseName;
  private final List<String> collectionNames;

  public ArtifactIdIndexProbe(MongoClient client, String databaseName, List<String> collectionNames) {
    this.client = client;
    this.databaseName = databaseName;
    this.collectionNames = List.copyOf(collectionNames);
  }

  /**
   * What the store reports, or why it could not be read. An unreadable store yields a status
   * carrying the reason rather than an exception, so no caller has to defend against one.
   */
  public record Status(List<String> withIndex, List<String> withoutIndex, Optional<String> unreadable) {

    public Status {
      withIndex = List.copyOf(withIndex);
      withoutIndex = List.copyOf(withoutIndex);
    }

    public boolean complete() {
      return unreadable.isEmpty() && withoutIndex.isEmpty();
    }

    public String describe() {
      if (unreadable.isPresent()) {
        return "index state unknown: " + unreadable.get();
      }
      if (withoutIndex.isEmpty()) {
        return "unique " + ID_FIELD + " index present on " + withIndex.size() + " artifact collections";
      }
      return "no unique " + ID_FIELD + " index on " + String.join(", ", withoutIndex)
          + " - the store enforces no uniqueness there and answers every lookup by identifier with a"
          + " collection scan; provision it with the admin tool's artifactServer-initDB task";
    }
  }

  /**
   * Matches on the index key and its unique flag rather than on a name, because the name a
   * provisioning step produces is derived from the key and carries no meaning of its own.
   */
  public Status inspect() {
    List<String> withIndex = new ArrayList<>();
    List<String> withoutIndex = new ArrayList<>();
    try {
      for (String collectionName : collectionNames) {
        if (hasUniqueIdIndex(collectionName)) {
          withIndex.add(collectionName);
        } else {
          withoutIndex.add(collectionName);
        }
      }
    } catch (RuntimeException e) {
      String reason = e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
      return new Status(List.of(), List.of(), Optional.of(reason));
    }
    return new Status(withIndex, withoutIndex, Optional.empty());
  }

  private boolean hasUniqueIdIndex(String collectionName) {
    // into() reads the cursor and closes it, which a for-each over the iterable would leave open
    List<Document> indexes = client.getDatabase(databaseName).getCollection(collectionName)
        .listIndexes().into(new ArrayList<>());
    for (Document index : indexes) {
      Document key = index.get("key", Document.class);
      if (key != null && key.containsKey(ID_FIELD) && index.getBoolean("unique", false)) {
        return true;
      }
    }
    return false;
  }
}
