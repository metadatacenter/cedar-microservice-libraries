package org.metadatacenter.config;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;

public class OpensearchMappingsConfig {

  private HashMap<String, Object> doc;

  @JsonProperty("_doc")
  public HashMap<String, Object> getDoc() {
    return doc;
  }

  @JsonProperty("_doc")
  public void setDoc(HashMap<String, Object> doc) {
    this.doc = doc;
  }

}
