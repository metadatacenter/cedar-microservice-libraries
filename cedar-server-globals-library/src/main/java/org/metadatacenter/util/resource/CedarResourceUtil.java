package org.metadatacenter.util.resource;

public class CedarResourceUtil {

  private CedarResourceUtil() {
  }

  public static String extractUUID(String id) {
    if (id != null) {
      int pos = id.lastIndexOf('/');
      if (pos > -1) {
        return id.substring(pos + 1);
      }
    }
    return null;
  }
}
