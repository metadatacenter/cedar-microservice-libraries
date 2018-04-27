package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.mongodb.MongoClient;
import org.metadatacenter.exception.TemplateServerResourceNotFoundException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.dao.mongodb.TemplateFieldDaoMongoDB;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateFieldService;

import java.io.IOException;
import java.util.List;

public class TemplateFieldServiceMongoDB extends GenericTemplateServiceMongoDB<String, JsonNode> implements
    TemplateFieldService<String, JsonNode> {

  private final TemplateFieldDaoMongoDB templateFieldDao;

  public TemplateFieldServiceMongoDB(MongoClient mongoClient, String db, String
      templateFieldsCollection) {
    this.templateFieldDao = new TemplateFieldDaoMongoDB(mongoClient, db, templateFieldsCollection);
  }

  @Override
  public JsonNode createTemplateField(JsonNode templateField) throws IOException {
    return templateFieldDao.create(templateField);
  }

  @Override
  public List<JsonNode> findAllTemplateFields(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx
      includeExclude) throws IOException {
    return templateFieldDao.findAll(limit, offset, fieldNames, includeExclude);
  }

  @Override
  public JsonNode findTemplateField(String templateFieldId) throws IOException,
      ProcessingException {
    return templateFieldDao.find(templateFieldId);
  }

  @Override
  public JsonNode updateTemplateField(String templateFieldId, JsonNode content) throws
      TemplateServerResourceNotFoundException, IOException {
    return templateFieldDao.update(templateFieldId, content);
  }

  @Override
  public void deleteTemplateField(String templateFieldId) throws TemplateServerResourceNotFoundException,
      IOException {
    templateFieldDao.delete(templateFieldId);
  }

  @Override
  public void deleteAllTemplateFields() {
    templateFieldDao.deleteAll();
  }

  @Override
  public long count() {
    return templateFieldDao.count();
  }

}
