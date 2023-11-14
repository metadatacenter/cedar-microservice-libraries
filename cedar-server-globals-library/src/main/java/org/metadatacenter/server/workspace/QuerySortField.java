package org.metadatacenter.server.workspace;

public class QuerySortField {

  private final String name;
  private final String fieldName;
  private final QuerySortFieldType type;

  public QuerySortField(String name, String fieldName, QuerySortFieldType type) {
    this.name = name;
    this.fieldName = fieldName;
    this.type = type;
  }

  public String getName() {
    return name;
  }

  public String getFieldName() {
    return fieldName;
  }

  public boolean isTextual() {
    return QuerySortFieldType.TEXTUAL == type;
  }
}
