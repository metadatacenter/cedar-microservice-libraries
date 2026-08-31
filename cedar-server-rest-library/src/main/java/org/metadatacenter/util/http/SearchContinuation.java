package org.metadatacenter.util.http;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.rest.exception.CedarAssertionException;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

/**
 * A caller's position in a deep search, small enough to travel in a query parameter.
 *
 * <p>A deep page is served by resuming the search at the last row of the previous one, inside a point
 * in time opened when the walk started. Both of those are values the server would otherwise have to
 * remember for every caller, so they travel with the caller instead.
 *
 * <p>The token is not signed, because nothing in it grants anything. The query, the filters and the
 * permission clause are rebuilt from the request on every page, so a forged or borrowed token can
 * only move a caller around inside the results they can already read. It carries the user and a
 * fingerprint of the search so that a token replayed by another user, or against a different query,
 * is refused rather than quietly answering from the wrong result set.
 */
public final class SearchContinuation {

  /**
   * What a caller passes to begin a walk, in the parameter they will pass tokens in afterwards.
   */
  public static final String START = "start";

  private static final int VERSION = 1;

  private final int version;
  private final String pointInTimeId;
  private final List<Object> searchAfter;
  private final String userId;
  private final String queryFingerprint;
  private final long rowsSeen;
  private final long totalCount;

  @JsonCreator
  public SearchContinuation(@JsonProperty("v") int version,
                            @JsonProperty("pit") String pointInTimeId,
                            @JsonProperty("after") List<Object> searchAfter,
                            @JsonProperty("user") String userId,
                            @JsonProperty("query") String queryFingerprint,
                            @JsonProperty("seen") long rowsSeen,
                            @JsonProperty("total") long totalCount) {
    this.version = version;
    this.pointInTimeId = pointInTimeId;
    this.searchAfter = searchAfter;
    this.userId = userId;
    this.queryFingerprint = queryFingerprint;
    this.rowsSeen = rowsSeen;
    this.totalCount = totalCount;
  }

  public static SearchContinuation of(String pointInTimeId, Object[] searchAfter, String userId,
                                      String queryFingerprint, long rowsSeen, long totalCount) {
    return new SearchContinuation(VERSION, pointInTimeId, List.of(searchAfter), userId, queryFingerprint, rowsSeen,
        totalCount);
  }

  public static boolean isStart(String value) {
    return START.equals(value);
  }

  @JsonProperty("v")
  public int getVersion() {
    return version;
  }

  @JsonProperty("pit")
  public String getPointInTimeId() {
    return pointInTimeId;
  }

  @JsonProperty("after")
  public List<Object> getSearchAfter() {
    return searchAfter;
  }

  @JsonProperty("user")
  public String getUserId() {
    return userId;
  }

  @JsonProperty("query")
  public String getQueryFingerprint() {
    return queryFingerprint;
  }

  @JsonProperty("seen")
  public long getRowsSeen() {
    return rowsSeen;
  }

  @JsonProperty("total")
  public long getTotalCount() {
    return totalCount;
  }

  @JsonIgnore
  public Object[] getSearchAfterValues() {
    return searchAfter.toArray();
  }

  public String encode() {
    try {
      byte[] json = JsonMapper.MAPPER.writeValueAsBytes(this);
      return Base64.getUrlEncoder().withoutPadding().encodeToString(json);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("A search continuation could not be written", e);
    }
  }

  /**
   * The token as the caller sent it, refused unless it is a token this server issued, to this user,
   * for this search. An edited one answers 400 rather than rows from elsewhere in the index.
   */
  public static SearchContinuation decode(String value, String userId, String queryFingerprint) throws CedarException {
    SearchContinuation continuation;
    try {
      byte[] json = Base64.getUrlDecoder().decode(value);
      continuation = JsonMapper.MAPPER.readValue(json, SearchContinuation.class);
    } catch (IllegalArgumentException | IOException e) {
      throw refuse("The continuation is not a token this server issued!");
    }
    if (continuation.version != VERSION) {
      throw refuse("The continuation was issued by another version of this server. Start the walk again!");
    }
    if (continuation.pointInTimeId == null || continuation.pointInTimeId.isEmpty()
        || continuation.searchAfter == null || continuation.searchAfter.isEmpty()) {
      throw refuse("The continuation is missing the position it should resume from!");
    }
    if (!Objects.equals(continuation.userId, userId)) {
      throw refuse("The continuation belongs to another user!");
    }
    if (!Objects.equals(continuation.queryFingerprint, queryFingerprint)) {
      throw refuse("The continuation belongs to a different search. The query, the filters and the sort must "
          + "stay as they were while paging through one!");
    }
    return continuation;
  }

  /**
   * Identifies the search a token may be continued against. The limit is left out, because a caller
   * may take the rest of a walk in larger or smaller pages and the position does not depend on how it
   * was reached. The sort is in, because it is the order the position is expressed in.
   */
  public static String fingerprint(String query, String id, List<String> resourceTypes, String version,
                                   String publicationStatus, String categoryId, List<String> sortList) {
    String canonical = String.join("|",
        String.valueOf(query),
        String.valueOf(id),
        String.valueOf(resourceTypes),
        String.valueOf(version),
        String.valueOf(publicationStatus),
        String.valueOf(categoryId),
        String.valueOf(sortList));
    try {
      byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder();
      for (int i = 0; i < 8; i++) {
        hex.append(String.format("%02x", digest[i]));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is not available", e);
    }
  }

  private static CedarException refuse(String message) {
    return new CedarAssertionException(message).badRequest();
  }
}
