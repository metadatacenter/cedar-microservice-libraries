package org.metadatacenter.server.logging;

import ch.vorburger.mariadb4j.DB;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * EmbeddedLogStore rebuilds the install command that mariaDB4j builds, so that the install can take a TMPDIR of its
 * own. Another mariaDB4j version may build that command differently, or pass a temporary directory itself.
 */
@Tag("integration")
class EmbeddedLogStoreTest {

  @Test
  void theCopiedInstallCommandComesFromTheMariaDB4jVersionOnTheClasspath() throws Exception {
    String version = mariaDB4jVersionOnTheClasspath();
    assertEquals(EmbeddedLogStore.COPIED_FROM_MARIADB4J_VERSION, version,
        "mariaDB4j " + version + " is on the classpath, but EmbeddedLogStore copies the install command that "
            + "DB.createDBInstallProcess builds in mariaDB4j " + EmbeddedLogStore.COPIED_FROM_MARIADB4J_VERSION
            + ". Compare DB.createDBInstallProcess in " + version + " with both copies of PrivateTemporaryDirectoryDB, "
            + "in EmbeddedCedarMySql (cedar-test-support-library) and EmbeddedLogStore "
            + "(cedar-logging-operations-library). Update both copies and both COPIED_FROM_MARIADB4J_VERSION "
            + "constants, or remove both copies if " + version
            + " passes a temporary directory to the install itself.");
  }

  /** The version that mariaDB4j's Maven build recorded in the jar that supplies {@link DB} to this JVM. */
  private static String mariaDB4jVersionOnTheClasspath() throws Exception {
    Path jar = Path.of(DB.class.getProtectionDomain().getCodeSource().getLocation().toURI());
    try (JarFile file = new JarFile(jar.toFile())) {
      JarEntry entry = file.getJarEntry("META-INF/maven/ch.vorburger.mariaDB4j/mariaDB4j-core/pom.properties");
      assertNotNull(entry, jar + " records no mariaDB4j-core version");
      Properties properties = new Properties();
      try (InputStream in = file.getInputStream(entry)) {
        properties.load(in);
      }
      return properties.getProperty("version");
    }
  }
}
