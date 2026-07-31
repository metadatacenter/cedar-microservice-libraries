package org.metadatacenter.server.logging.dbmodel.agg;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Hourly-UTC rollup of who is calling, keyed by user + auth source + individual API key.
 * <p>
 * {@code apiKeyHash} separates a single user's individual keys (CEDAR now supports multiple keys per
 * user + rotation); it is null for token/anonymous traffic and for history (pre-apiKeyHash rows).
 * Kept separate from {@link AggRequestHourly} so user x endpoint x hour never explodes.
 */
@Entity
@Table(name = "agg_request_user_hourly",
    uniqueConstraints = @UniqueConstraint(
        name = "UK_agg_request_user_hourly",
        columnNames = {"hourUtc", "userId", "authSource", "apiKeyHash"}),
    indexes = {
        @Index(columnList = "hourUtc", name = "IDX_agg_request_user_hourly_hourUtc"),
        @Index(columnList = "userId", name = "IDX_agg_request_user_hourly_userId"),
        @Index(columnList = "apiKeyHash", name = "IDX_agg_request_user_hourly_apiKeyHash"),
    })
public class AggRequestUserHourly {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  private Instant hourUtc;

  @Column(length = 70)
  private String userId;

  @Column(length = 9)
  private String authSource;

  /** md5 of the API key (never the key itself); null unless authSource = apiKey. */
  @Column(length = 32)
  private String apiKeyHash;

  private long reqCount;
  private long errorCount;
  private long sumHandlerNanos;

  public Long getId() {
    return id;
  }

  public Instant getHourUtc() {
    return hourUtc;
  }

  public String getUserId() {
    return userId;
  }

  public String getAuthSource() {
    return authSource;
  }

  public String getApiKeyHash() {
    return apiKeyHash;
  }

  public long getReqCount() {
    return reqCount;
  }

  public long getErrorCount() {
    return errorCount;
  }

  public long getSumHandlerNanos() {
    return sumHandlerNanos;
  }
}
