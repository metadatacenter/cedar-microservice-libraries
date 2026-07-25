package org.metadatacenter.util.http;

import org.apache.http.HttpEntity;
import org.apache.http.util.EntityUtils;
import org.metadatacenter.exception.CedarProcessingException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class CedarEntityUtil {

  private CedarEntityUtil() {
  }

  public static String toString(HttpEntity entity) throws CedarProcessingException {
    String es;
    try {
      es = EntityUtils.toString(entity, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new CedarProcessingException(e);
    }
    return es;
  }
}
