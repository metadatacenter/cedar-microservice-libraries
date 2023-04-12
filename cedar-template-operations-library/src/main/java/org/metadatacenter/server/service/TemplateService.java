package org.metadatacenter.server.service;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;

import java.io.IOException;
import java.util.List;

public interface TemplateService<K, T> {

  T createTemplate(T template) throws IOException;

  List<T> findAllTemplates() throws IOException;

  List<T> findAllTemplates(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException;

  List<T> findAllTemplates(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx includeExclude)
      throws IOException;

  T findTemplate(K templateId) throws IOException;

  T updateTemplate(K templateId, T content) throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplate(K templateId) throws ArtifactServerResourceNotFoundException, IOException;

  boolean existsTemplate(K templateId) throws IOException;

  void deleteAllTemplates();

  long count();
}
