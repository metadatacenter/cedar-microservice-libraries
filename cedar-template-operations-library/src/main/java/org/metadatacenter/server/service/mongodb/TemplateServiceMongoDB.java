package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;
import org.metadatacenter.server.dao.mongodb.TemplateDaoMongoDB;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateService;

import java.io.IOException;
import java.util.List;

public class TemplateServiceMongoDB extends GenericTemplateServiceMongoDB<String, JsonNode> implements TemplateService<String, JsonNode> {

  private final TemplateDaoMongoDB templateDao;

  public TemplateServiceMongoDB(MongoClient mongoClient, String db, String templatesCollection) {
    this.templateDao = new TemplateDaoMongoDB(mongoClient, db, templatesCollection);
  }

  @Override
  public JsonNode createTemplate(JsonNode template) throws IOException {
    return templateDao.create(template);
  }

  @Override
  public List<JsonNode> findAllTemplates() throws IOException {
    return templateDao.findAll();
  }

  @Override
  public List<JsonNode> findAllTemplates(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException {
    return templateDao.findAll(fieldNames, includeExclude);
  }

  @Override
  public List<JsonNode> findAllTemplates(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx
      includeExclude) throws IOException {
    return templateDao.findAll(limit, offset, fieldNames, includeExclude);
  }

  @Override
  public JsonNode findTemplate(String templateId) throws IOException {
    return templateDao.find(templateId);
  }

  @Override
  public ArtifactWithRevision<JsonNode> findTemplateWithRevision(String templateId) throws IOException {
    return templateDao.findWithRevision(templateId);
  }

  @Override
  public long getTemplateRevision(String templateId) throws ArtifactServerResourceNotFoundException {
    return templateDao.getRevision(templateId);
  }

  @Override
  public JsonNode updateTemplate(String templateId, JsonNode content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException,
      IOException {
    return templateDao.update(templateId, content, expectedRevision);
  }

  @Override
  public void deleteTemplate(String templateId) throws ArtifactServerResourceNotFoundException, IOException {
    templateDao.delete(templateId);
  }

  @Override
  public boolean existsTemplate(String templateId) throws IOException {
    return templateDao.exists(templateId);
  }

  @Override
  public void deleteAllTemplates() {
    templateDao.deleteAll();
  }

  @Override
  public long count() {
    return templateDao.count();
  }


}
