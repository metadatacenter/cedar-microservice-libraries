package org.metadatacenter.server.logging.dao;

import io.dropwizard.hibernate.AbstractDAO;
import org.hibernate.SessionFactory;
import org.metadatacenter.server.logging.dbmodel.ApplicationCypherLog;

public class ApplicationCypherLogDAO extends AbstractDAO<ApplicationCypherLog> {

  public ApplicationCypherLogDAO(SessionFactory factory) {
    super(factory);
  }

  public Long create(ApplicationCypherLog log) {
    return persist(log).getId();
  }

}