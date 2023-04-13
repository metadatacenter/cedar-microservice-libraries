package org.metadatacenter.server.dao.mongodb;

import com.mongodb.MongoClient;

public class TemplateElementDaoMongoDB extends GenericLDDaoMongoDB {

  public TemplateElementDaoMongoDB(MongoClient mongoClient, String dbName, String
      collectionName) {
    super(mongoClient, dbName, collectionName);
  }
}
