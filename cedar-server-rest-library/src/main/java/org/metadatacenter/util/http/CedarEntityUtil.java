package org.metadatacenter.util.http;

import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
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
    } catch (IOException | ParseException e) {
      throw new CedarProcessingException(e);
    }
    return es;
  }
}
