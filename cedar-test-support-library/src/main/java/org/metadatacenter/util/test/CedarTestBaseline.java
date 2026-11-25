package org.metadatacenter.util.test;

import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The environment a test JVM starts from: the process environment with every CEDAR address taken
 * away from the services the host runs.
 *
 * <p>Server tests build their environment by copying {@link CedarEnvironmentSource#getAll()} and
 * redirecting the stores they embed. On a workstation the copied map is the developer's profile, so
 * every store a test does not embed would otherwise still point at the running stack: a Mongo-only
 * suite would hold the developer's Neo4j, OpenSearch and MySQL addresses. The baseline keeps the
 * profile's other values and replaces the addresses. Every {@code CEDAR_*_HOST} becomes the loopback
 * address. A server's HTTP or admin port becomes 0, so an application a test boots without choosing
 * its port binds one the operating system assigns rather than the stack's. Every other port becomes
 * 1, where nothing listens, so a client the test did not redirect fails at once instead of reaching
 * a live store.
 *
 * <p>{@link CedarEmbeddedProcessSessionListener} installs the baseline when the JVM's test session
 * opens, and {@link CedarEnvironmentClassIsolationExtension} restores it after each test class.
 */
public final class CedarTestBaseline {

  static final String LOOPBACK = "127.0.0.1";
  static final String ASSIGNED_PORT = "0";
  static final String DEAD_PORT = "1";

  private static final Pattern HOST = Pattern.compile("CEDAR_.+_HOST");
  private static final Pattern SERVER_PORT = Pattern.compile("CEDAR_.+_(HTTP|ADMIN|STOP)_PORT");
  private static final Pattern PORT = Pattern.compile("CEDAR_.+_PORT");

  private static volatile Map<String, String> baseline;

  private CedarTestBaseline() {
  }

  /** Returns the baseline derived from the given process environment. */
  static Map<String, String> from(Map<String, String> processEnvironment) {
    Map<String, String> environment = new LinkedHashMap<>(processEnvironment);
    environment.replaceAll((name, value) -> {
      if (HOST.matcher(name).matches()) {
        return LOOPBACK;
      }
      if (SERVER_PORT.matcher(name).matches()) {
        return ASSIGNED_PORT;
      }
      if (PORT.matcher(name).matches()) {
        return DEAD_PORT;
      }
      return value;
    });
    return Collections.unmodifiableMap(environment);
  }

  /**
   * Installs the baseline as the JVM's environment override, replacing whatever override a test
   * class left. The baseline is computed from the process environment once and reused.
   */
  public static void install() {
    Map<String, String> current = baseline;
    if (current == null) {
      synchronized (CedarTestBaseline.class) {
        current = baseline;
        if (current == null) {
          current = from(System.getenv());
          baseline = current;
        }
      }
    }
    CedarEnvironmentSource.setOverride(current);
  }

  /** Whether the installed override is the baseline. */
  public static boolean isInstalled() {
    Map<String, String> current = baseline;
    return current != null && CedarEnvironmentSource.hasOverride()
        && CedarEnvironmentSource.getAll().equals(current);
  }
}
