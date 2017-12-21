package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.mongodb.MongoClient;
import org.metadatacenter.exception.TemplateServerResourceNotFoundException;
import org.metadatacenter.server.dao.mongodb.TemplateElementDaoMongoDB;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateElementService;

import java.io.IOException;
import java.util.List;

public class TemplateElementServiceMongoDB extends GenericTemplateServiceMongoDB<String, JsonNode> implements
    TemplateElementService<String, JsonNode> {

  private final TemplateElementDaoMongoDB templateElementDao;

  public TemplateElementServiceMongoDB(MongoClient mongoClient, String db, String
      templateElementsCollection) {
    this.templateElementDao = new TemplateElementDaoMongoDB(mongoClient, db, templateElementsCollection);
  }

  @Override
  public JsonNode createTemplateElement(JsonNode templateElement) throws IOException {
    return templateElementDao.create(templateElement);
  }

  @Override
  public List<JsonNode> findAllTemplateElements() throws IOException {
    return templateElementDao.findAll();
  }

  @Override
  public List<JsonNode> findAllTemplateElements(List<String> fieldNames, FieldNameInEx includeExclude) throws
      IOException {
    return templateElementDao.findAll(fieldNames, includeExclude);
  }

  @Override
  public List<JsonNode> findAllTemplateElements(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx
      includeExclude) throws IOException {
    return templateElementDao.findAll(limit, offset, fieldNames, includeExclude);
  }

  @Override
  public JsonNode findTemplateElement(String templateElementId) throws IOException, ProcessingException {
    return templateElementDao.find(templateElementId);
  }

  @Override
  public JsonNode updateTemplateElement(String templateElementId, JsonNode content)
      throws TemplateServerResourceNotFoundException, IOException {
    return templateElementDao.update(templateElementId, content);
  }

  @Override
  public void deleteTemplateElement(String templateElementId) throws
      TemplateServerResourceNotFoundException, IOException {
    templateElementDao.delete(templateElementId);
  }

  @Override
  public boolean existsTemplateElement(String templateElementId) throws IOException {
    return templateElementDao.exists(templateElementId);
  }

  @Override
  public void deleteAllTemplateElements() {
    templateElementDao.deleteAll();
  }

  @Override
  public long count() {
    return templateElementDao.count();
  }

}
