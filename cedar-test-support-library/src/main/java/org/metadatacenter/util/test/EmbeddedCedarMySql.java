package org.metadatacenter.util.test;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * An in-process MariaDB for integration tests, replacing the live MySQL. Note the stand-in is
 * MariaDB 11 while the deployment runs MySQL 8 (cedar-infra-mysql): protocol- and
 * dialect-compatible for the Hibernate-managed schemas tested here, but not the identical
 * engine; MariaDB4j ships no MySQL binaries. Call
 * startAndRedirectEnvironment from a static initializer, before the DropwizardAppRule starts the
 * application: it boots the embedded server on a random port (so it can never collide with, or
 * write into, a real MySQL) and redirects the CEDAR MySQL environment variables for the given
 * prefix - CEDAR_MESSAGING_MYSQL for the messaging store, CEDAR_LOG_MYSQL for the application
 * log store. The database is created by the connector (createDatabaseIfNotExist) and the schema
 * by Hibernate (hbm2ddl auto-update), so no external DDL is involved.
 *
 * The platform database binaries come from an OS-activated profile in the consumer's pom
 * (mariaDB4j-db-macos-arm64 / -linux64 / -winx64), since profiles do not travel with
 * dependencies.
 */
public final class EmbeddedCedarMySql {

  private static DB db;

  private EmbeddedCedarMySql() {
  }

  public static synchronized void startAndRedirectEnvironment(String envPrefix, Map<String, String> extraEnvironment) {
    if (db == null) {
      try {
        DBConfigurationBuilder configuration = DBConfigurationBuilder.newBuilder();
        // Maven reactor workers share java.io.tmpdir, but must never unpack or
        // delete another test JVM's database binaries or writable store.
        configuration.setBaseDir(Files.createTempDirectory("mdb-base-").toFile());
        configuration.setDataDir(Files.createTempDirectory("mdb-data-").toFile());
        // Every starting server, the install's bootstrap server among them,
        // deletes each #sql file in its temporary directory. Each instance
        // therefore needs its own temporary directory.
        configuration.setTmpDir(Files.createTempDirectory("mdb-tmp-").toString());
        configuration.setDeletingTemporaryBaseAndDataDirsOnShutdown(true);
        configuration.addArg("--bind-address=127.0.0.1");
        configuration.setPort(0); // 0 picks a free port
        db = PrivateTemporaryDirectoryDB.create(configuration.build());
        db.start();
      } catch (Exception e) {
        throw new IllegalStateException("Could not start the embedded MariaDB", e);
      }
    }
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put(envPrefix + "_HOST", "127.0.0.1");
    environment.put(envPrefix + "_PORT", String.valueOf(db.getConfiguration().getPort()));
    environment.put(envPrefix + "_USER", "root");
    environment.put(envPrefix + "_PASSWORD", "");
    environment.putAll(extraEnvironment);
    CedarEnvironmentSource.setOverride(environment);
  }

  static synchronized void stop() {
    if (db == null) {
      return;
    }
    try {
      db.stop();
    } catch (Exception e) {
      throw new IllegalStateException("Could not stop the embedded MariaDB", e);
    } finally {
      db = null;
    }
  }

  /**
   * mariaDB4j passes the configured temporary directory to the server, but runs mariadb-install-db without one. The
   * install's bootstrap server then uses TMPDIR, or /tmp when TMPDIR is unset, and every test JVM of a build shares
   * that directory. This database sets TMPDIR to the configured directory for the install. Otherwise the install
   * command is the one that {@link DB#createDBInstallProcess()} builds in mariaDB4j 3.3.1.
   */
  private static final class PrivateTemporaryDirectoryDB extends DB {

    private PrivateTemporaryDirectoryDB(DBConfiguration configuration) {
      super(configuration);
    }

    /**
     * Creates the database, prepares its directories and installs it, as {@link DB#newEmbeddedDB(DBConfiguration)}
     * does. The data directory is always new, so the install always runs.
     */
    static DB create(DBConfiguration configuration) throws ManagedProcessException {
      PrivateTemporaryDirectoryDB db = new PrivateTemporaryDirectoryDB(configuration);
      db.prepareDirectories();
      db.unpackEmbeddedDb();
      db.install();
      return db;
    }

    @Override
    protected ManagedProcess createDBInstallProcess() throws ManagedProcessException, IOException {
      if (configuration.isWindows()) {
        return super.createDBInstallProcess();
      }
      ManagedProcessBuilder builder =
          new ManagedProcessBuilder(configuration.getExecutable(DBConfiguration.Executable.InstallDB));
      builder.setOutputStreamLogDispatcher(getOutputStreamLogDispatcher("mysql_install_db"));
      builder.getEnvironment().put(configuration.getOSLibraryEnvironmentVarName(),
          configuration.getLibDir().getAbsolutePath());
      builder.getEnvironment().put("TMPDIR", configuration.getTmpDir().getAbsolutePath());
      builder.setWorkingDirectory(configuration.getBaseDir());
      builder.addFileArgument("--datadir", configuration.getDataDir());
      builder.addFileArgument("--basedir", configuration.getBaseDir());
      builder.addArgument("--no-defaults");
      builder.addArgument("--force");
      builder.addArgument("--skip-name-resolve");
      return builder.build();
    }
  }

}
