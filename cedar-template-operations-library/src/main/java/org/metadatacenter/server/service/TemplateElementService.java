package org.metadatacenter.server.service;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;

import java.io.IOException;
import java.util.List;

public interface TemplateElementService<K, T> {

  T createTemplateElement(T templateElement) throws IOException;

  List<T> findAllTemplateElements() throws IOException;

  List<T> findAllTemplateElements(List<String> fieldName, FieldNameInEx includeExclude) throws IOException;

  List<T> findAllTemplateElements(Integer limit, Integer offset, List<String> fieldName, FieldNameInEx
      includeExclude) throws IOException;

  T findTemplateElement(K templateElementId) throws IOException;

  ArtifactWithRevision<T> findTemplateElementWithRevision(K templateElementId) throws IOException;

  long getTemplateElementRevision(K templateElementId) throws ArtifactServerResourceNotFoundException;

  T updateTemplateElement(K templateElementId, T content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateElement(K templateElementId) throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateElement(K templateElementId, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  boolean existsTemplateElement(K templateElementId) throws IOException;

  void deleteAllTemplateElements();

  long count();
}
