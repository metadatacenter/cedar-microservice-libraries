package org.metadatacenter.server.dao.mongodb;

import com.mongodb.client.MongoClient;
import com.mongodb.client.model.Filters;

import static org.metadatacenter.model.ModelNodeNames.SCHEMA_IS_BASED_ON;

public class TemplateInstanceDaoMongoDB extends GenericLDDaoMongoDB {

  public TemplateInstanceDaoMongoDB(MongoClient mongoClient, String dbName, String
      collectionName) {
    super(mongoClient, dbName, collectionName);
  }

  public long countReferencingTemplate(String templateId) {
    return entityCollection.countDocuments(Filters.eq(SCHEMA_IS_BASED_ON, templateId));
  }
  public java.util.List<String> findReferencingTemplateIds(String templateId) {
    java.util.List<String> ids = new java.util.ArrayList<>();
    entityCollection.find(Filters.eq(SCHEMA_IS_BASED_ON, templateId))
        .projection(com.mongodb.client.model.Projections.include("@id"))
        .forEach(document -> ids.add(document.getString("@id")));
    return ids;
  }
}
