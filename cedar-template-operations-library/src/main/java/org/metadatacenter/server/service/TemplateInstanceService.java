package org.metadatacenter.server.service;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.dao.ArtifactWithRevision;

import java.io.IOException;
import java.util.List;

public interface TemplateInstanceService<K, T> {

  T createTemplateInstance(T templateInstance) throws IOException;

  default ArtifactWithRevision<T> createTemplateInstanceWithRevision(T templateInstance) throws IOException {
    throw new UnsupportedOperationException("The implementation must return the revision assigned by insertion");
  }

  default ArtifactWithRevision<T> createTemplateInstanceWithRevision(T instance, Long templateRevision) throws IOException {
    throw new UnsupportedOperationException("The instance write must fence its validated template revision");
  }

  default T updateTemplateInstance(K id, T content, long expectedRevision, Long templateRevision)
      throws ArtifactServerResourceNotFoundException, IOException {
    throw new UnsupportedOperationException("The instance write must fence its validated template revision");
  }

  List<T> findAllTemplateInstances() throws IOException;

  List<T> findAllTemplateInstances(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException;

  List<T> findAllTemplateInstances(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx
      includeExclude) throws IOException;

  T findTemplateInstance(K templateInstanceId) throws IOException;

  ArtifactWithRevision<T> findTemplateInstanceWithRevision(K templateInstanceId) throws IOException;

  long getTemplateInstanceRevision(K templateInstanceId) throws ArtifactServerResourceNotFoundException;

  T updateTemplateInstance(K templateInstanceId, T content, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateInstance(K templateInstanceId) throws ArtifactServerResourceNotFoundException, IOException;

  void deleteTemplateInstance(K templateInstanceId, long expectedRevision)
      throws ArtifactServerResourceNotFoundException, IOException;

  void deleteAllTemplateInstances();

  long count();

  long countReferencingTemplate(K templateId);

  /** Unfiltered content-store references, including instances absent from the workspace graph. */
  List<String> findReferencingTemplateIds(K templateId);
}
