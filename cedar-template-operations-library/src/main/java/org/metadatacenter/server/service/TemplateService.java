package org.metadatacenter.server.service;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;

import java.io.IOException;
import java.util.List;

public interface TemplateService<K, T> {

  T createTemplate(T template) throws IOException;

  List<T> findAllTemplates() throws IOException;

  List<T> findAllTemplates(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException;

  List<T> findAllTemplates(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx includeExclude)
      throws IOException;

  T findTemplate(K templateId) throws IOException;

  ArtifactWithRevision<T> findTemplateWithRevision(K templateId) throws IOException;

  long getTemplateRevision(K templateId) throws ArtifactServerResourceNotFoundException;

  T updateTemplate(K templateId, T content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplate(K templateId) throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplate(K templateId, long expectedRevision) throws ArtifactServerResourceNotFoundException, IOException;

  boolean existsTemplate(K templateId) throws IOException;

  void deleteAllTemplates();

  long count();
}
