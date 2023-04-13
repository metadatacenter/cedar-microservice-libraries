package org.metadatacenter.server.dao;

import org.metadatacenter.exception.ArtifactServerResourceNotFoundException;
import org.metadatacenter.server.service.FieldNameInEx;

import java.io.IOException;
import java.util.List;

public interface GenericDao<K, T> {

  T create(T element) throws IOException;

  List<T> findAll() throws IOException;

  List<T> findAll(Integer count, Integer page, List<String> fieldNames, FieldNameInEx includeExclude) throws
      IOException;

  List<T> findAll(List<String> fieldNames, FieldNameInEx includeExclude) throws IOException;

  T find(K id) throws IOException;

  T update(K id, T content) throws ArtifactServerResourceNotFoundException, IOException;

  void delete(K id) throws ArtifactServerResourceNotFoundException, IOException;

  boolean exists(K id) throws IOException;

  void deleteAll();

  long count();
}
