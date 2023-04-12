package org.metadatacenter.server.dao.mongodb;

import com.mongodb.MongoClient;

public class TemplateDaoMongoDB extends GenericLDDaoMongoDB {

  public TemplateDaoMongoDB(MongoClient mongoClient, String dbName, String collectionName) {
    super(mongoClient, dbName, collectionName);
  }

}