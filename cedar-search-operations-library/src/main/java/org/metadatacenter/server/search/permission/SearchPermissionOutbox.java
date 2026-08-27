package org.metadatacenter.server.search.permission;

import org.metadatacenter.server.search.SearchPermissionQueueEvent;

import java.util.List;

interface SearchPermissionOutbox extends AutoCloseable {

  record Entry(String outboxId, SearchPermissionQueueEvent event) {
  }

  String append(SearchPermissionQueueEvent event);

  List<Entry> pending(int limit);

  void remove(String outboxId);

  long count();

  @Override
  void close();
}
