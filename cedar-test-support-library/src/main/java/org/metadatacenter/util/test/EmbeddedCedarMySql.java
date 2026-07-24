package org.metadatacenter.util.test;

import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;

import java.util.HashMap;
import java.util.Map;

/**
 * An in-process MariaDB for integration tests, replacing the live MySQL. Call
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
        configuration.setPort(0); // 0 picks a free port
        db = DB.newEmbeddedDB(configuration.build());
        db.start();
      } catch (Exception e) {
        throw new IllegalStateException("Could not start the embedded MariaDB", e);
      }
    }
    Map<String, String> environment = new HashMap<>(System.getenv());
    environment.put(envPrefix + "_HOST", "127.0.0.1");
    environment.put(envPrefix + "_PORT", String.valueOf(db.getConfiguration().getPort()));
    environment.put(envPrefix + "_USER", "root");
    environment.put(envPrefix + "_PASSWORD", "");
    environment.putAll(extraEnvironment);
    TestUtil.setEnv(environment);
  }

}
