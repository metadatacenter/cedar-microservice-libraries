package org.metadatacenter.cedar.util.dw;

import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.hibernate.HibernateBundle;
import org.metadatacenter.config.HibernateConfig;

import java.util.Map;

/**
 * A Hibernate bundle over one of the databases {@code cedar-main.yml} configures.
 *
 * <p>Three services each carried their own copy of this, thirty-two lines differing only in the
 * class name, the configuration type parameter, and which {@code HibernateConfig} they read: the
 * worker and monitor take the application log's database, messaging takes its own. The database is
 * the only real difference, so it is the argument, and the bundle is one class.
 *
 * <p>The configuration is read when the bundle is built rather than when Dropwizard asks for the
 * data source. Both happen after {@code CedarConfig} is loaded and it does not change, so this is
 * the same answer a moment earlier.
 */
public class CedarHibernateBundle<T extends Configuration> extends HibernateBundle<T> {

  private final HibernateConfig databaseConfig;

  public CedarHibernateBundle(HibernateConfig databaseConfig, Class<?> entity, Class<?>... entities) {
    super(entity, entities);
    this.databaseConfig = databaseConfig;
  }

  @Override
  public PooledDataSourceFactory getDataSourceFactory(T configuration) {
    DataSourceFactory database = new DataSourceFactory();
    database.setUrl(databaseConfig.getUrl());
    database.setUser(databaseConfig.getUser());
    database.setPassword(databaseConfig.getPassword());
    database.setDriverClass(databaseConfig.getDriverClass());
    Map<String, String> properties = database.getProperties();
    properties.putAll(databaseConfig.getProperties());
    return database;
  }
}
