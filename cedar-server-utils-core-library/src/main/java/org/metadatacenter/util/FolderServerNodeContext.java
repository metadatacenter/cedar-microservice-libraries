package org.metadatacenter.util;

import java.util.HashMap;
import java.util.Map;

public class FolderServerNodeContext {

  protected static final Map<String, String> CONTEXT;

  static {
    CONTEXT = new HashMap<>();
    CONTEXT.put("pav", "http://purl.org/pav/");
    CONTEXT.put("oslc", "http://open-services.net/ns/core#");
    CONTEXT.put("bibo", "http://purl.org/ontology/bibo/");
    CONTEXT.put("schema", "http://schema.org/");
  }

  private FolderServerNodeContext() {
  }

  public static Map<String, String> getContext() {
    return CONTEXT;
  }
}
