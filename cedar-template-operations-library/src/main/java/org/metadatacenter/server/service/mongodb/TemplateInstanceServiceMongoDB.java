package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.mongodb.TemplateInstanceDaoMongoDB;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateInstanceService;

import java.io.IOException;
import java.util.List;

public class TemplateInstanceServiceMongoDB extends GenericTemplateServiceMongoDB<String, JsonNode> implements TemplateInstanceService<String, JsonNode> {

  private final TemplateInstanceDaoMongoDB templateInstanceDao;

  public TemplateInstanceServiceMongoDB(MongoClient mongoClient, String db, String templateInstancesCollection) {
    this.templateInstanceDao = new TemplateInstanceDaoMongoDB(mongoClient, db, templateInstancesCollection);
  }

  @Override
  public JsonNode createTemplateInstance(JsonNode templateInstance) throws IOException {
    return templateInstanceDao.create(templateInstance);
  }

  @Override
  public List<JsonNode> findAllTemplateInstances() throws IOException {
    return templateInstanceDao.findAll();
  }

  @Override
  public List<JsonNode> findAllTemplateInstances(List<String> fieldNames, FieldNameInEx includeExclude) throws
      IOException {
    return templateInstanceDao.findAll(fieldNames, includeExclude);
  }

  @Override
  public List<JsonNode> findAllTemplateInstances(Integer limit, Integer offset, List<String> fieldNames,
                                                 FieldNameInEx includeExclude) throws IOException {
    return templateInstanceDao.findAll(limit, offset, fieldNames, includeExclude);
  }

  @Override
  public JsonNode findTemplateInstance(String templateInstanceId) throws IOException {
    return templateInstanceDao.find(templateInstanceId);
  }

  @Override
  public JsonNode updateTemplateInstance(String templateInstanceId, JsonNode content) throws
      ArtifactServerResourceNotFoundException, IOException {
    return templateInstanceDao.update(templateInstanceId, content);
  }

  @Override
  public void deleteTemplateInstance(String templateInstanceId) throws ArtifactServerResourceNotFoundException,
      IOException {
    templateInstanceDao.delete(templateInstanceId);
  }

  @Override
  public void deleteAllTemplateInstances() {
    templateInstanceDao.deleteAll();
  }

  @Override
  public long count() {
    return templateInstanceDao.count();
  }

  @Override
  public long countReferencingTemplate(String templateId) {
    return templateInstanceDao.countReferencingTemplate(templateId);
  }

}
