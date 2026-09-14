package org.metadatacenter.cedar.util.dw.ratelimit;

public interface QuotaStore extends AutoCloseable {
  record Decision(boolean allowed, long retryAfterSeconds, String policy) {}
  Decision acquire(String userId, String policy);
  @Override void close();
}
