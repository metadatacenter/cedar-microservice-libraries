package org.metadatacenter.server.dao.mongodb;

import com.mongodb.MongoClient;

public class TemplateFieldDaoMongoDB extends GenericLDDaoMongoDB {

  public TemplateFieldDaoMongoDB(MongoClient mongoClient, String dbName, String
      collectionName) {
    super(mongoClient, dbName, collectionName);
  }
}
