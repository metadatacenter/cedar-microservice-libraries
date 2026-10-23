package org.metadatacenter.util.test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
