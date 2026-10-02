package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.mongodb.client.MongoClient;
import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;
import org.metadatacenter.server.dao.mongodb.TemplateInstanceDaoMongoDB;
import org.metadatacenter.server.dao.mongodb.TemplateReferenceGuard;
import org.metadatacenter.server.service.FieldNameInEx;
import org.metadatacenter.server.service.TemplateInstanceService;

import java.io.IOException;
import java.util.List;

public class TemplateInstanceServiceMongoDB extends GenericTemplateServiceMongoDB<String, JsonNode> implements TemplateInstanceService<String, JsonNode> {

  private final TemplateInstanceDaoMongoDB templateInstanceDao;
  private TemplateReferenceGuard referenceGuard;

  public TemplateInstanceServiceMongoDB(MongoClient mongoClient, String db, String templateInstancesCollection) {
    this.templateInstanceDao = new TemplateInstanceDaoMongoDB(mongoClient, db, templateInstancesCollection);
  }

  public TemplateInstanceServiceMongoDB(MongoClient client, String db, String instances, String templates) {
    this(client, db, instances);
    referenceGuard = new TemplateReferenceGuard(client, db, templates, instances);
  }

  @Override
  public JsonNode createTemplateInstance(JsonNode instance) throws IOException {
    return createTemplateInstanceWithRevision(instance).content();
  }

  @Override
  public ArtifactWithRevision<JsonNode> createTemplateInstanceWithRevision(JsonNode instance) throws IOException {
    if (referenceGuard == null) return templateInstanceDao.createWithRevision(instance);
    try {
      return referenceGuard.write(instance, -1, () -> templateInstanceDao.createWithRevision(instance));
    } catch (ArtifactServerResourceNotFoundException e) {
      throw new IOException(e);
    }
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
  public ArtifactWithRevision<JsonNode> findTemplateInstanceWithRevision(String templateInstanceId) throws IOException {
    return templateInstanceDao.findWithRevision(templateInstanceId);
  }

  @Override
  public long getTemplateInstanceRevision(String templateInstanceId) throws ArtifactServerResourceNotFoundException {
    return templateInstanceDao.getRevision(templateInstanceId);
  }

  @Override
  public JsonNode updateTemplateInstance(String templateInstanceId, JsonNode content, long expectedRevision) throws
      ArtifactServerResourceNotFoundException, IOException {
    return referenceGuard == null ? templateInstanceDao.update(templateInstanceId, content, expectedRevision)
        : referenceGuard.write(content, expectedRevision,
            () -> templateInstanceDao.update(templateInstanceId, content, expectedRevision));
  }

  @Override
  public void deleteTemplateInstance(String templateInstanceId) throws ArtifactServerResourceNotFoundException,
      IOException {
    templateInstanceDao.delete(templateInstanceId);
  }

  @Override
  public void deleteTemplateInstance(String templateInstanceId, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException {
    templateInstanceDao.delete(templateInstanceId, expectedRevision);
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

  @Override
  public List<String> findReferencingTemplateIds(String templateId) {
    return templateInstanceDao.findReferencingTemplateIds(templateId);
  }
}
