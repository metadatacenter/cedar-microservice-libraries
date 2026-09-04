package org.metadatacenter.cedar.util.dw;

import org.metadatacenter.config.environment.CedarEnvironmentVariable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

/**
 * What this service is, and what it was built from.
 *
 * <p>Assembled from what a running JVM can see about itself rather than from anything stamped in
 * at build time, so it needs no change to the build and cannot go stale against it. The two facts
 * that matter after a deploy are the version the environment declares and the timestamp on the
 * artifact the JVM actually loaded: when a service reports the new version but an artifact older
 * than the release, the box was pulled and not rebuilt, and that is the failure that looks exactly
 * like a successful deploy from every other angle.
 */
final class CedarBuildInfo {

  private CedarBuildInfo() {
  }

  /**
   * The report for one service.
   *
   * @param anchor      a class from the service's own code, used to find the artifact it was loaded from
   * @param application the service's display name
   * @param environment the sandbox the configuration was built from, read for the version variables
   */
  static Map<String, Object> forService(Class<?> anchor, String application, Map<String, String> environment) {
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("application", application);
    report.put("version", environment.get(CedarEnvironmentVariable.CEDAR_VERSION.getName()));
    report.put("versionModifier", environment.get(CedarEnvironmentVariable.CEDAR_VERSION_MODIFIER.getName()));

    report.put("host", hostName());
    report.put("pid", ProcessHandle.current().pid());

    report.put("java.version", System.getProperty("java.version"));
    report.put("java.vendor", System.getProperty("java.vendor"));
    report.put("jvm.name", System.getProperty("java.vm.name"));

    long startedAt = ManagementFactory.getRuntimeMXBean().getStartTime();
    report.put("startedAt", Instant.ofEpochMilli(startedAt).toString());
    report.put("uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime());

    report.putAll(artifact(anchor));
    return report;
  }

  /**
   * Where the service's own code came from and when it was written.
   *
   * <p>A packaged service resolves to its jar and a service run from a build tree to that tree's
   * class directory; both carry a modification time and both answer the question. Nothing here is
   * required to succeed — a class loader is free to have no code source — and a report missing the
   * artifact is worth more than an endpoint that fails.
   */
  private static Map<String, Object> artifact(Class<?> anchor) {
    Map<String, Object> report = new LinkedHashMap<>();
    try {
      URL location = anchor.getProtectionDomain().getCodeSource().getLocation();
      File file = new File(URI.create(location.toString()));
      report.put("artifactPath", file.getAbsolutePath());
      report.put("artifactType", file.isDirectory() ? "classes" : "jar");
      if (file.exists()) {
        report.put("artifactBuiltAt", Instant.ofEpochMilli(file.lastModified()).toString());
        if (file.isFile()) {
          report.put("artifactSizeBytes", file.length());
        }
      } else {
        report.put("artifactBuiltAt", null);
      }
    } catch (RuntimeException e) {
      report.put("artifactPath", null);
      report.put("artifactError", e.getMessage());
    }
    report.put("implementationVersion", implementationVersion(anchor));
    return report;
  }

  /** {@code Implementation-Version} from the manifest of the jar this class came from, where there is one. */
  private static String implementationVersion(Class<?> anchor) {
    Package pkg = anchor.getPackage();
    if (pkg != null && pkg.getImplementationVersion() != null) {
      return pkg.getImplementationVersion();
    }
    try (InputStream in = anchor.getClassLoader().getResourceAsStream("META-INF/MANIFEST.MF")) {
      if (in == null) {
        return null;
      }
      Attributes attributes = new Manifest(in).getMainAttributes();
      return attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
    } catch (IOException e) {
      return null;
    }
  }

  private static String hostName() {
    try {
      return InetAddress.getLocalHost().getHostName();
    } catch (UnknownHostException e) {
      return null;
    }
  }
}
