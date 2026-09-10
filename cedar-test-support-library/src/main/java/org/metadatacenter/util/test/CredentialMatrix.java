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
 * A table-driven check of which credentials are anybody at all. Each row is one operation; each cell
 * is the status a given credential must receive.
 *
 * <p>{@link PermissionMatrix} answers the question after this one. Its actors are an owner, another
 * user, an administrator and an unauthenticated caller, so its grid says who may do what once the
 * caller has been established. It has no way to express a credential that exists and should not
 * work: a key its holder disabled, a key that was deleted, a header in a scheme the server does not
 * accept. Those all resolve to the same expectation on every route, which is what makes the grid
 * cheap to write and worth writing. A disabled API key authenticated for as long as it did because
 * nothing asked.
 *
 * <p>A matrix must name exactly one accepted credential. Without it a grid of refusals passes when
 * the fixture is broken and the route refuses everyone, which is the failure this is most likely to
 * hide.
 *
 * <p>Typical use:
 * <pre>
 *   CredentialMatrix matrix = new CredentialMatrix(baseUrl);
 *   matrix.accepted("the user's own enabled key", authHeader);
 *   matrix.refused("no credential at all", null);
 *   matrix.refused("a disabled API key", "apiKey " + disabledKey);
 *   matrix.when("GET", "/users/" + uuid).accepting(200);
 *   matrix.when("POST", "/users/" + uuid + "/api-keys", "{}").accepting(201);
 *   matrix.verify();
 * </pre>
 *
 * <p>Every failure is collected, so one run reports the whole divergence instead of the first cell
 * that differs.
 */
public final class CredentialMatrix {

  /** The status a credential that authenticates nobody must receive, whatever the route. */
  public static final int REFUSED = 401;

  private final String baseUrl;
  private final Map<String, Credential> credentials = new LinkedHashMap<>();
  private final List<Row> rows = new ArrayList<>();

  /** @param baseUrl e.g. {@code "http://localhost:" + SERVER.getLocalPort()} */
  public CredentialMatrix(String baseUrl) {
    this.baseUrl = baseUrl;
  }

  /**
   * The credential every row must serve. Exactly one is required: it is what proves a row of
   * refusals is a statement about the credentials rather than about a route nobody can reach.
   */
  public CredentialMatrix accepted(String label, String authorizationHeader) {
    if (authorizationHeader == null) {
      throw new IllegalArgumentException("An accepted credential needs an Authorization header");
    }
    return add(new Credential(label, authorizationHeader, true, new int[0]));
  }

  /** A credential every row must refuse with 401. A null header sends no Authorization at all. */
  public CredentialMatrix refused(String label, String authorizationHeader) {
    return refused(label, authorizationHeader, REFUSED);
  }

  /**
   * A credential every row must refuse. The statuses are stated only where a route answers something
   * other than 401, which is worth writing down rather than working around.
   */
  public CredentialMatrix refused(String label, String authorizationHeader, int... acceptableStatuses) {
    if (acceptableStatuses.length == 0) {
      throw new IllegalArgumentException("At least one acceptable status is required");
    }
    return add(new Credential(label, authorizationHeader, false, acceptableStatuses));
  }

  private CredentialMatrix add(Credential credential) {
    if (credentials.put(credential.label, credential) != null) {
      throw new IllegalArgumentException("Two credentials share the label " + credential.label);
    }
    return this;
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
    Assertions.assertFalse(rows.isEmpty(), "The credential matrix is empty, so it asserts nothing");
    long acceptedCount = credentials.values().stream().filter(c -> c.accepted).count();
    Assertions.assertEquals(1, acceptedCount,
        "A credential matrix needs exactly one accepted credential, or a grid of refusals proves nothing");
    Assertions.assertTrue(credentials.values().stream().anyMatch(c -> !c.accepted),
        "A credential matrix with nothing to refuse asserts nothing about credentials");

    List<String> divergences = new ArrayList<>();
    for (Row row : rows) {
      if (row.acceptedStatuses.length == 0) {
        divergences.add(row.describe() + ": no status declared for the accepted credential");
        continue;
      }
      for (Credential credential : credentials.values()) {
        int[] acceptable = credential.accepted ? row.acceptedStatuses : credential.acceptableStatuses;
        int status;
        try {
          status = probe(row, credential);
        } catch (Exception e) {
          divergences.add(row.describe() + " with " + credential.label + ": request failed - " + e);
          continue;
        }
        if (Arrays.stream(acceptable).noneMatch(code -> code == status)) {
          divergences.add(row.describe() + " with " + credential.label + ": expected "
              + Arrays.toString(acceptable) + " but got " + status);
        }
      }
    }
    Assertions.assertEquals(0, divergences.size(),
        "Credential matrix diverged:\n" + String.join("\n", divergences));
  }

  private int probe(Row row, Credential credential) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(baseUrl + row.path));
    if (credential.authorizationHeader != null) {
      builder.header("Authorization", credential.authorizationHeader);
    }
    if (row.jsonBody != null) {
      builder.header("Content-Type", "application/json");
      builder.method(row.verb, HttpRequest.BodyPublishers.ofString(row.jsonBody));
    } else {
      builder.method(row.verb, HttpRequest.BodyPublishers.noBody());
    }
    row.headers.forEach(builder::header);
    HttpResponse<String> response = TestHttpClient.send(builder.build());
    return response.statusCode();
  }

  /** One credential state, and what it must be answered with. */
  private record Credential(String label, String authorizationHeader, boolean accepted,
                            int[] acceptableStatuses) {
  }

  /** One operation, and the status the accepted credential must receive from it. */
  public final class Row {
    private final String verb;
    private final String path;
    private final String jsonBody;
    private final Map<String, String> headers = new LinkedHashMap<>();
    private int[] acceptedStatuses = new int[0];

    private Row(String verb, String path, String jsonBody) {
      this.verb = verb;
      this.path = path;
      this.jsonBody = jsonBody;
    }

    /**
     * Declare the status the accepted credential must receive. More than one code may be acceptable
     * where the operation's outcome depends on state a matrix does not control.
     */
    public Row accepting(int... acceptableStatuses) {
      if (acceptableStatuses.length == 0) {
        throw new IllegalArgumentException("At least one acceptable status is required");
      }
      this.acceptedStatuses = acceptableStatuses;
      return this;
    }

    /** Add one request header to every probe in this row. */
    public Row header(String name, String value) {
      headers.put(name, value);
      return this;
    }

    /** Continue the table with another operation. */
    public Row when(String nextVerb, String nextPath) {
      return CredentialMatrix.this.when(nextVerb, nextPath);
    }

    /** Continue the table with another operation carrying a JSON body. */
    public Row when(String nextVerb, String nextPath, String nextJsonBody) {
      return CredentialMatrix.this.when(nextVerb, nextPath, nextJsonBody);
    }

    /** Run the whole table (convenience, so a chain can end without leaving the builder). */
    public void verify() {
      CredentialMatrix.this.verify();
    }

    private String describe() {
      return verb + " " + path;
    }
  }

}
