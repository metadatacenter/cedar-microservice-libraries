package org.metadatacenter.server.service;

import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.metadatacenter.exception.TemplateServerResourceNotFoundException;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.model.provenance.ProvenanceInfo;
import org.metadatacenter.util.provenance.ProvenanceUtil;

import java.io.IOException;
import java.util.List;

public interface TemplateFieldService<K, T> {

  @NonNull T createTemplateField(@NonNull T templateField) throws IOException;

  @NonNull List<T> findAllTemplateFields(Integer limit, Integer offset, List<String> fieldName, FieldNameInEx
      includeExclude) throws IOException;

  T findTemplateField(@NonNull String templateFieldId) throws IOException, ProcessingException;

  @NonNull T updateTemplateField(@NonNull K templateFieldId, @NonNull T content) throws
      TemplateServerResourceNotFoundException, IOException;

  void deleteTemplateField(@NonNull K templateFieldId) throws TemplateServerResourceNotFoundException, IOException;

  void deleteAllTemplateFields();

  long count();

  void saveNewFieldsAndReplaceIds(T genericInstance, ProvenanceInfo pi, ProvenanceUtil provenanceUtil, LinkedDataUtil
      linkedDataUtil) throws IOException;
}