package org.metadatacenter.util.mongo;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.constant.MongoConstants;
import org.metadatacenter.util.json.JsonUtils;

public final class MongoUtils {

  private MongoUtils() {
  }

  public static JsonNode removeIdField(JsonNode node) {
    return JsonUtils.removeField(node, MongoConstants.ID);
  }
}
