package org.metadatacenter.server.queue.util;

import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.AbstractLogger;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * An SLF4J logger that records what it was asked to log, so a test can assert on the level, the
 * rendered message, and - the point of these tests - whether a stack trace was attached.
 * <p>
 * {@link AbstractLogger} normalizes every logging overload down to a single call, so recording that
 * one call captures {@code error(String)} and {@code error(String, Throwable)} alike, and the
 * throwable being null is exactly the distinction under test. The record list is concurrent because
 * one test drives this from many threads at once.
 */
class RecordingLogger extends AbstractLogger {

  /**
   * One logging call: the level it was logged at, the message as rendered, and the attached
   * throwable, which is null when the caller passed no cause.
   */
  record Entry(Level level, String message, Throwable throwable) {

    boolean hasStackTrace() {
      return throwable != null;
    }
  }

  private final List<Entry> entries = new CopyOnWriteArrayList<>();

  List<Entry> entries() {
    return List.copyOf(entries);
  }

  Entry entry(int index) {
    return entries.get(index);
  }

  int count() {
    return entries.size();
  }

  List<Entry> withStackTrace() {
    return entries.stream().filter(Entry::hasStackTrace).toList();
  }

  @Override
  protected void handleNormalizedLoggingCall(Level level, Marker marker, String messagePattern,
                                             Object[] arguments, Throwable throwable) {
    entries.add(new Entry(level, messagePattern, throwable));
  }

  @Override
  protected String getFullyQualifiedCallerName() {
    return RecordingLogger.class.getName();
  }

  // Every level is enabled: a test asserting that a call was suppressed should be reading the
  // recorded entries, never a level check that quietly dropped it

  @Override
  public boolean isTraceEnabled() {
    return true;
  }

  @Override
  public boolean isTraceEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isDebugEnabled() {
    return true;
  }

  @Override
  public boolean isDebugEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isInfoEnabled() {
    return true;
  }

  @Override
  public boolean isInfoEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isWarnEnabled() {
    return true;
  }

  @Override
  public boolean isWarnEnabled(Marker marker) {
    return true;
  }

  @Override
  public boolean isErrorEnabled() {
    return true;
  }

  @Override
  public boolean isErrorEnabled(Marker marker) {
    return true;
  }
}
