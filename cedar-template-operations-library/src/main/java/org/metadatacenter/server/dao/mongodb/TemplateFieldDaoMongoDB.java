package org.metadatacenter.server.dao.mongodb;

import com.mongodb.client.MongoClient;

public class TemplateFieldDaoMongoDB extends GenericLDDaoMongoDB {

  public TemplateFieldDaoMongoDB(MongoClient mongoClient, String dbName, String
      collectionName) {
    super(mongoClient, dbName, collectionName);
  }
}
