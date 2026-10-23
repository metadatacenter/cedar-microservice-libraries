package org.metadatacenter.server.logging;

import ch.vorburger.exec.ManagedProcess;
import ch.vorburger.exec.ManagedProcessBuilder;
import ch.vorburger.exec.ManagedProcessException;
import ch.vorburger.mariadb4j.DB;
import ch.vorburger.mariadb4j.DBConfiguration;
import ch.vorburger.mariadb4j.DBConfigurationBuilder;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.dropwizard.hibernate.UnitOfWorkAwareProxyFactory;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.cfg.Configuration;
import org.hibernate.context.internal.ManagedSessionContext;
import org.metadatacenter.server.logging.agg.LogAggregationService;
import org.metadatacenter.server.logging.dao.ApplicationCypherLogDAO;
import org.metadatacenter.server.logging.dao.ApplicationRequestLogDAO;
import org.metadatacenter.server.logging.dao.agg.AggregationRollupDAO;
import org.metadatacenter.server.logging.dao.agg.LogAggregationStateDAO;
import org.metadatacenter.server.logging.dbmodel.ApplicationCypherLog;
import org.metadatacenter.server.logging.dbmodel.ApplicationLogReceipt;
import org.metadatacenter.server.logging.dbmodel.ApplicationRequestLog;
import org.metadatacenter.server.logging.dbmodel.agg.AggCypherHourly;
import org.metadatacenter.server.logging.dbmodel.agg.AggCypherOutlier;
import org.metadatacenter.server.logging.dbmodel.agg.AggCypherQueryCatalog;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestHourly;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestOutlier;
import org.metadatacenter.server.logging.dbmodel.agg.AggRequestUserHourly;
import org.metadatacenter.server.logging.dbmodel.agg.LogAggregationState;
import org.metadatacenter.server.logging.model.AppLogMessage;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.List;
import java.util.function.Function;

/**
 * The application log database in-process: a MariaDB on a free port, the schema the servers' Hibernate bundle
 * creates, and the two frozen history tables the backfill reads. The connection settings are the servers' own, so
 * timestamps pass through the same time zone. One database serves every test in the JVM, and {@link #clear()} empties
 * it between tests.
 */
final class EmbeddedLogStore {

  private static final List<Class<?>> ENTITIES = List.of(ApplicationRequestLog.class, ApplicationCypherLog.class,
      ApplicationLogReceipt.class, AggRequestHourly.class, AggCypherHourly.class, AggRequestUserHourly.class,
      AggCypherQueryCatalog.class, AggRequestOutlier.class, AggCypherOutlier.class, LogAggregationState.class);

  private static final List<String> TABLES = List.of("log_request", "log_cypher", "log_event_receipt",
      "agg_request_hourly", "agg_cypher_hourly", "agg_request_user_hourly", "agg_cypher_query_catalog",
      "agg_request_outlier", "agg_cypher_outlier", "log_aggregation_state", "log_request_pre284",
      "log_cypher_pre284");

  private static EmbeddedLogStore instance;

  final SessionFactory sessionFactory;
  /** The application log writer, as the worker builds it. */
  final AppLoggerExecutorService writer;
  /** The aggregation engine, as the worker builds it. */
  final LogAggregationService aggregation;

  private EmbeddedLogStore(int port) {
    Configuration configuration = new Configuration();
    ENTITIES.forEach(configuration::addAnnotatedClass);
    configuration.setProperty("hibernate.connection.driver_class", "com.mysql.cj.jdbc.Driver");
    configuration.setProperty("hibernate.connection.url", "jdbc:mysql://127.0.0.1:" + port + "/cedar_log"
        + "?createDatabaseIfNotExist=true&serverTimezone=America/Los_Angeles&useSSL=false"
        + "&allowPublicKeyRetrieval=true");
    configuration.setProperty("hibernate.connection.username", "root");
    configuration.setProperty("hibernate.connection.password", "");
    configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
    configuration.setProperty("hibernate.hbm2ddl.auto", "update");
    configuration.setProperty("hibernate.current_session_context_class", "managed");
    sessionFactory = configuration.buildSessionFactory();
    inTransaction(session -> {
      session.createNativeMutationQuery("CREATE TABLE IF NOT EXISTS log_request_pre284 LIKE log_request").executeUpdate();
      session.createNativeMutationQuery("CREATE TABLE IF NOT EXISTS log_cypher_pre284 LIKE log_cypher").executeUpdate();
      return null;
    });
    UnitOfWorkAwareProxyFactory proxies = new UnitOfWorkAwareProxyFactory("hibernate", sessionFactory);
    writer = proxies.create(AppLoggerExecutorService.class,
        new Class<?>[]{ApplicationRequestLogDAO.class, ApplicationCypherLogDAO.class},
        new Object[]{new ApplicationRequestLogDAO(sessionFactory), new ApplicationCypherLogDAO(sessionFactory)});
    aggregation = proxies.create(LogAggregationService.class,
        new Class<?>[]{AggregationRollupDAO.class, LogAggregationStateDAO.class},
        new Object[]{new AggregationRollupDAO(sessionFactory), new LogAggregationStateDAO(sessionFactory)});
  }

  static synchronized EmbeddedLogStore start() {
    if (instance == null) {
      try {
        DBConfigurationBuilder configuration = DBConfigurationBuilder.newBuilder();
        configuration.setBaseDir(Files.createTempDirectory("mdb-base-").toFile());
        configuration.setDataDir(Files.createTempDirectory("mdb-data-").toFile());
        // Every starting server, the install's bootstrap server among them, deletes each #sql file in its temporary
        // directory. Concurrent test JVMs share java.io.tmpdir, so each instance needs its own temporary directory.
        configuration.setTmpDir(Files.createTempDirectory("mdb-tmp-").toString());
        configuration.setDeletingTemporaryBaseAndDataDirsOnShutdown(true);
        configuration.addArg("--bind-address=127.0.0.1");
        configuration.setPort(0);
        DB db = PrivateTemporaryDirectoryDB.create(configuration.build());
        db.start();
        instance = new EmbeddedLogStore(db.getConfiguration().getPort());
      } catch (Exception e) {
        throw new IllegalStateException("Could not start the embedded MariaDB", e);
      }
    }
    instance.clear();
    return instance;
  }

  void clear() {
    inTransaction(session -> {
      for (String table : TABLES) {
        session.createNativeMutationQuery("DELETE FROM " + table).executeUpdate();
      }
      return null;
    });
  }

  /** Runs the work in a session bound the way {@code @UnitOfWork} binds one, and commits it. */
  <T> T inTransaction(Function<Session, T> work) {
    Session session = sessionFactory.openSession();
    ManagedSessionContext.bind(session);
    Transaction transaction = session.beginTransaction();
    try {
      T result = work.apply(session);
      transaction.commit();
      return result;
    } catch (RuntimeException e) {
      transaction.rollback();
      throw e;
    } finally {
      ManagedSessionContext.unbind(sessionFactory);
      session.close();
    }
  }

  /** Runs a native statement and returns its rows. */
  @SuppressWarnings("unchecked")
  List<Object[]> rows(String sql) {
    return inTransaction(session -> (List<Object[]>) session.createNativeQuery(sql).getResultList());
  }

  /** Runs a native statement that selects one column, and returns its values. */
  @SuppressWarnings("unchecked")
  List<Object> column(String sql) {
    return inTransaction(session -> (List<Object>) session.createNativeQuery(sql).getResultList());
  }

  long count(String sql) {
    return inTransaction(session -> ((Number) session.createNativeQuery(sql).getSingleResult()).longValue());
  }

  void execute(String sql) {
    inTransaction(session -> session.createNativeMutationQuery(sql).executeUpdate());
  }

  /**
   * The message as the worker receives it from the queue, logged at the given time. The queue carries JSON, and the
   * log time is set only there.
   */
  static AppLogMessage at(Instant logTime, AppLogMessage message) {
    ObjectNode json = JsonMapper.STRICT_MAPPER.valueToTree(message);
    json.put("logTime", logTime.toString());
    try {
      return JsonMapper.TOLERANT_MAPPER.treeToValue(json, AppLogMessage.class);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * mariaDB4j passes the configured temporary directory to the server, but runs mariadb-install-db without one. The
   * install's bootstrap server then uses TMPDIR, or /tmp when TMPDIR is unset, and every test JVM of a build shares
   * that directory. This database sets TMPDIR to the configured directory for the install. Otherwise the install
   * command is the one that {@link DB#createDBInstallProcess()} builds in mariaDB4j 3.3.1. The test support library's
   * EmbeddedCedarMySql carries the same class, since that library depends on this one.
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
