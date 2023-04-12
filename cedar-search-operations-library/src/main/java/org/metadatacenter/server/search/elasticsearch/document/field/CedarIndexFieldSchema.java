package org.metadatacenter.server.search.elasticsearch.document.field;

import com.fasterxml.jackson.annotation.JsonInclude;

// Ignores null values
@JsonInclude(JsonInclude.Include.NON_NULL)

/**
 * This class represents the field information that will be indexed for CEDAR schemas (i.e., templates and elements).
 */
public class CedarIndexFieldSchema {

  private String fieldName;
  // For instances based on controlled terms, it corresponds to the value of the @id field. It is set to null otherwise
  private String fieldSemanticType;
  // Field value type, following to the Elasticsearch data types
  private String fieldValueType;

  // Used by Jackson
  public CedarIndexFieldSchema() {}

  public CedarIndexFieldSchema(String fieldName, String fieldSemanticType, String fieldValueType) {
    this.fieldName = fieldName;
    this.fieldSemanticType = fieldSemanticType;
    this.fieldValueType = fieldValueType;
  }

  public String getFieldName() {
    return fieldName;
  }

  public void setFieldName(String fieldName) {
    this.fieldName = fieldName;
  }

  public String getFieldSemanticType() {
    return fieldSemanticType;
  }

  public void setFieldSemanticType(String fieldSemanticType) {
    this.fieldSemanticType = fieldSemanticType;
  }

  public String getFieldValueType() {
    return fieldValueType;
  }

  public void setFieldValueType(String fieldValueType) {
    this.fieldValueType = fieldValueType;
  }

  @Override
  public String toString() {
    return "CedarIndexFieldSchema{" +
        "fieldName='" + fieldName + '\'' +
        ", fieldSemanticType='" + fieldSemanticType + '\'' +
        ", fieldValueType='" + fieldValueType + '\'' +
        '}';
  }

}


