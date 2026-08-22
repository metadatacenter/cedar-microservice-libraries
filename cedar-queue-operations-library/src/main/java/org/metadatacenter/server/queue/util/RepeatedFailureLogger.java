package org.metadatacenter.server.queue.util;

import org.slf4j.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Logs a failure that is expected to recur, at ERROR, without flooding the log.
 * <p>
 * A queue (Redis) outage does not fail once. It fails every enqueue and every consumer retry, for
 * as long as it lasts, and the failure originates deep inside a request filter or a consumer loop,
 * so its stack trace runs to roughly a hundred frames. Repeating those frames per occurrence buries
 * every other line in the log.
 * <p>
 * Only the first occurrence carries a stack trace. Later ones keep the exception's type and
 * message, which is what distinguishes one cause from another, and a running total, so the pattern
 * stays visible to log monitoring and the frames below the first occurrence are not repeated.
 */
public class RepeatedFailureLogger {

  private final AtomicLong occurrenceCount = new AtomicLong();

  /**
   * Logs one occurrence and returns the running total, this one included. The count is reported as
   * "(N {countNoun} since startup)", appended to the message.
   */
  public long report(Logger log, String message, String countNoun, Exception cause) {
    long occurrences = occurrenceCount.incrementAndGet();
    String counted = message + " (" + occurrences + " " + countNoun + " since startup)";
    // incrementAndGet is atomic, so exactly one caller observes 1 and logs the stack trace
    if (occurrences == 1) {
      log.error(counted + " Subsequent occurrences are logged without a stack trace.", cause);
    } else {
      log.error(counted + " Cause: " + cause);
    }
    return occurrences;
  }

  public long getCount() {
    return occurrenceCount.get();
  }
}
