package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A table-driven check of who may do what. Each row is one operation; each cell is the status a
 * given actor must receive.
 *
 * <p>CEDAR's purpose is controlled sharing of metadata, so an authorization regression is the worst
 * kind: silent, and it exposes other people's data. Per-endpoint tests tend to cover the owner's
 * happy path and little else, which leaves the interesting cells — another user, an unauthenticated
 * caller, an administrator — untested. Writing the expectations as a table makes the whole grid
 * visible, so a missing cell is obvious in review rather than invisible.
 *
 * <p>Typical use:
 * <pre>
 *   Map&lt;Actor, String&gt; actors = Map.of(OWNER, user1Header, OTHER_USER, user2Header, ADMIN, adminHeader);
 *   PermissionMatrix matrix = new PermissionMatrix(baseUrl, actors);
 *   matrix.when("GET", "/groups/" + id)
 *       .expect(ANONYMOUS, 401)
 *       .expect(OWNER, 200)
 *       .expect(OTHER_USER, 403, 404);   // either is acceptable: hiding existence is also fine
 *   matrix.verify();
 * </pre>
 *
 * <p>Every failure is collected, so one run reports the whole divergence instead of the first cell
 * that differs. A run with no rows, or a row with no expectations, fails rather than passing
 * vacuously.
 */
public final class PermissionMatrix {

  /** Who is making the request. ANONYMOUS never carries credentials. */
  public enum Actor {
    ANONYMOUS,
    OWNER,
    OTHER_USER,
    ADMIN
  }

  private final String baseUrl;
  private final Map<Actor, String> authHeaders;
  private final List<Row> rows = new ArrayList<>();

  /**
   * @param baseUrl     e.g. {@code "http://localhost:" + SERVER.getLocalPort()}
   * @param authHeaders the Authorization header per actor; ANONYMOUS must not be present
   */
  public PermissionMatrix(String baseUrl, Map<Actor, String> authHeaders) {
    this.baseUrl = baseUrl;
    this.authHeaders = new LinkedHashMap<>(authHeaders);
    if (this.authHeaders.containsKey(Actor.ANONYMOUS)) {
      throw new IllegalArgumentException("ANONYMOUS must not have an Authorization header");
    }
  }

  /** Start a row for an operation with no request body. */
  public Row when(String verb, String path) {
    return when(verb, path, null);
  }

  /** Start a row for an operation with a JSON request body. */
  public Row when(String verb, String path, String jsonBody) {
    Row row = new Row(verb, path, jsonBody);
    rows.add(row);
    return row;
  }

  /** Probe every cell and assert the whole grid at once. */
  public void verify() {
    Assertions.assertFalse(rows.isEmpty(), "The permission matrix is empty, so it asserts nothing");
    StringBuilder failures = new StringBuilder();
    for (Row row : rows) {
      if (row.expectations.isEmpty()) {
        failures.append(row.describe()).append(": no expectations declared for this operation\n");
        continue;
      }
      for (Map.Entry<Actor, int[]> cell : row.expectations.entrySet()) {
        Actor actor = cell.getKey();
        int[] acceptable = cell.getValue();
        int status;
        try {
          status = probe(row, actor);
        } catch (Exception e) {
          failures.append(row.describe()).append(" as ").append(actor)
              .append(": request failed - ").append(e).append('\n');
          continue;
        }
        if (Arrays.stream(acceptable).noneMatch(code -> code == status)) {
          failures.append(row.describe()).append(" as ").append(actor)
              .append(": expected ").append(Arrays.toString(acceptable))
              .append(" but got ").append(status).append('\n');
        }
      }
    }
    Assertions.assertEquals(0, failures.length(), "Authorization matrix diverged:\n" + failures);
  }

  private int probe(Row row, Actor actor) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + row.path));
    if (actor != Actor.ANONYMOUS) {
      String header = authHeaders.get(actor);
      if (header == null) {
        throw new IllegalStateException("No Authorization header configured for actor " + actor);
      }
      builder.header("Authorization", header);
    }
    if (row.jsonBody != null) {
      builder.header("Content-Type", "application/json");
      builder.method(row.verb, HttpRequest.BodyPublishers.ofString(row.jsonBody));
    } else {
      builder.method(row.verb, HttpRequest.BodyPublishers.noBody());
    }
    row.headers.forEach(builder::header);
    HttpResponse<String> response = ProbeClient.send(builder.build());
    return response.statusCode();
  }

  /** One operation and the status each actor must receive. */
  public final class Row {
    private final String verb;
    private final String path;
    private final String jsonBody;
    private final Map<Actor, int[]> expectations = new LinkedHashMap<>();
    private final Map<String, String> headers = new LinkedHashMap<>();

    private Row(String verb, String path, String jsonBody) {
      this.verb = verb;
      this.path = path;
      this.jsonBody = jsonBody;
    }

    /**
     * Declare the status an actor must receive. More than one code may be acceptable — for example
     * an endpoint may legitimately answer either 403 or 404 to a user who may not see the resource,
     * since hiding its existence is also a valid choice.
     */
    public Row expect(Actor actor, int... acceptableStatuses) {
      if (acceptableStatuses.length == 0) {
        throw new IllegalArgumentException("At least one acceptable status is required");
      }
      expectations.put(actor, acceptableStatuses);
      return this;
    }

    /** Add one request header to every actor probe in this row. */
    public Row header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    /** Continue the table with another operation. */
    public Row when(String nextVerb, String nextPath) {
      return PermissionMatrix.this.when(nextVerb, nextPath);
    }

    /** Continue the table with another operation carrying a JSON body. */
    public Row when(String nextVerb, String nextPath, String nextJsonBody) {
      return PermissionMatrix.this.when(nextVerb, nextPath, nextJsonBody);
    }

    /** Run the whole table (convenience, so a chain can end without leaving the builder). */
    public void verify() {
      PermissionMatrix.this.verify();
    }

    private String describe() {
      return verb + " " + path;
    }
  }

}
