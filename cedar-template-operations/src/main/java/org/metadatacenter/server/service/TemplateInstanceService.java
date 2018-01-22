package org.metadatacenter.server.service;

import javax.management.InstanceNotFoundException;
import java.io.IOException;
import java.util.List;

public interface TemplateInstanceService<K, T> {

  T createTemplateInstance(T templateInstance) throws IOException;

  List<T> findAllTemplateInstances() throws IOException;

  List<T> findAllTemplateInstances(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException;

  List<T> findAllTemplateInstances(Integer limit, Integer offset, List<String> fieldNames, FieldNameInEx
      includeExclude) throws IOException;

  T findTemplateInstance(K templateInstanceId) throws IOException;

  T updateTemplateInstance(K templateInstanceId, T content) throws
      InstanceNotFoundException, IOException;

  void deleteTemplateInstance(K templateInstanceId) throws InstanceNotFoundException, IOException;

  void deleteAllTemplateInstances();

  long count();

  long countReferencingTemplate(K templateId);
}
