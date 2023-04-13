package org.metadatacenter.server.search;

/**
 * Wrap an elasticsearch internal id
 */
public class IndexedDocumentId {

  private String id;

  //for Jackson
  public IndexedDocumentId() {
  }

  public IndexedDocumentId(String id) {
    this.id = id;
  }

  public String getId() {
    return id;
  }
}
