package org.metadatacenter.server.service;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;

import java.io.IOException;
import java.util.List;

public interface TemplateFieldService<K, T> {

  T createTemplateField(T templateField) throws IOException;

  List<T> findAllTemplateFields(Integer limit, Integer offset, List<String> fieldName, FieldNameInEx includeExclude)
      throws IOException;

  T findTemplateField(String templateFieldId) throws IOException;

  ArtifactWithRevision<T> findTemplateFieldWithRevision(K templateFieldId) throws IOException;

  long getTemplateFieldRevision(K templateFieldId) throws ArtifactServerResourceNotFoundException;

  T updateTemplateField(K templateFieldId, T content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateField(K templateFieldId) throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateField(K templateFieldId, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteAllTemplateFields();

  long count();

}
