package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.dropwizard.db.DataSourceFactory;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.HibernateConfig;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CedarHibernateBundleTest {

  @Test
  void carriesTheCompletePoolBudgetIntoDropwizard() throws Exception {
    HibernateConfig config = new ObjectMapper().readValue("""
        {
          "url": "jdbc:mysql://localhost/cedar",
          "user": "cedar",
          "password": "secret",
          "driverClass": "com.mysql.cj.jdbc.Driver",
          "properties": {"hibernate.dialect": "org.hibernate.dialect.MySQLDialect"},
          "minSize": 2,
          "initialSize": 3,
          "maxSize": 20,
          "maxWaitForConnectionMillis": 15000,
          "validationQuery": "SELECT 1",
          "checkConnectionWhileIdle": true,
          "checkConnectionOnConnect": true,
          "validationIntervalMillis": 30000
        }
        """, HibernateConfig.class);

    DataSourceFactory database = CedarHibernateBundle.buildDataSourceFactory(config);

    assertEquals("jdbc:mysql://localhost/cedar", database.getUrl());
    assertEquals("cedar", database.getUser());
    assertEquals("secret", database.getPassword());
    assertEquals("com.mysql.cj.jdbc.Driver", database.getDriverClass());
    assertEquals("org.hibernate.dialect.MySQLDialect", database.getProperties().get("hibernate.dialect"));
    assertEquals(2, database.getMinSize());
    assertEquals(3, database.getInitialSize());
    assertEquals(20, database.getMaxSize());
    assertEquals(15000, database.getMaxWaitForConnection().toMilliseconds());
    assertEquals("SELECT 1", database.getValidationQuery().orElseThrow());
    assertTrue(database.getCheckConnectionWhileIdle());
    assertTrue(database.getCheckConnectionOnConnect());
    assertEquals(30000, database.getValidationInterval().toMilliseconds());
  }
}
