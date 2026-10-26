package org.metadatacenter.util.test;

import ch.vorburger.mariadb4j.DB;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A starting MariaDB server deletes every file in its temporary directory whose name begins with {@code #sql}, the
 * prefix of its temporary tables. The bootstrap server that mariadb-install-db runs does so too. The test JVMs of a
 * build share one temporary directory, so an embedded MariaDB that kept its temporary files there could delete a
 * temporary table that another JVM's install was still using, and that install aborted.
 */
@Tag("integration")
class EmbeddedCedarMySqlTest {

  @Test
  void startingLeavesAnotherProcessesTemporaryTablesInTheSharedTemporaryDirectory() throws IOException {
    // Start a new instance, so that its install runs inside this test.
    EmbeddedCedarMySql.stop();
    List<Path> foreignTables = new ArrayList<>();
    for (Path directory : sharedTemporaryDirectories()) {
      foreignTables.add(Files.createTempFile(directory, "#sql-", ".MAI"));
    }
    try {
      EmbeddedCedarMySql.startAndRedirectEnvironment("CEDAR_MESSAGING_MYSQL", Map.of());
      for (Path table : foreignTables) {
        assertTrue(Files.exists(table), "The embedded MariaDB deleted another process's temporary table " + table);
      }
    } finally {
      EmbeddedCedarMySql.stop();
      for (Path table : foreignTables) {
        Files.deleteIfExists(table);
      }
    }
  }

  /**
   * EmbeddedCedarMySql rebuilds the install command that mariaDB4j builds, so that the install can take a TMPDIR of
   * its own. Another mariaDB4j version may build that command differently, or pass a temporary directory itself.
   */
  @Test
  void theCopiedInstallCommandComesFromTheMariaDB4jVersionOnTheClasspath() throws Exception {
    String version = mariaDB4jVersionOnTheClasspath();
    assertEquals(EmbeddedCedarMySql.COPIED_FROM_MARIADB4J_VERSION, version,
        "mariaDB4j " + version + " is on the classpath, but EmbeddedCedarMySql copies the install command that "
            + "DB.createDBInstallProcess builds in mariaDB4j " + EmbeddedCedarMySql.COPIED_FROM_MARIADB4J_VERSION
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

  /**
   * The directories a MariaDB child shares with every other process unless it is given its own: the JVM's temporary
   * directory, and the directory the server falls back to, which is TMPDIR or else /tmp.
   */
  private static Set<Path> sharedTemporaryDirectories() {
    String environment = System.getenv("TMPDIR");
    Set<Path> directories = new LinkedHashSet<>();
    directories.add(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize());
    directories.add(Path.of(environment == null || environment.isEmpty() ? "/tmp" : environment)
        .toAbsolutePath().normalize());
    directories.removeIf(directory -> !Files.isDirectory(directory));
    return directories;
  }
}
