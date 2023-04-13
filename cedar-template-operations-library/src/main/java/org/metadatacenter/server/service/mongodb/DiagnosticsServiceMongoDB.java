package org.metadatacenter.server.service.mongodb;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.BasicDBObject;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.conversions.Bson;
import org.metadatacenter.server.service.DiagnosticsService;

import java.util.Date;

public class DiagnosticsServiceMongoDB implements DiagnosticsService<JsonNode> {

  private MongoDatabase database = null;

  public DiagnosticsServiceMongoDB(MongoClient mongoClient, String dbName) {
    database = mongoClient.getDatabase(dbName);
  }

  public JsonNode heartbeat() {
    ObjectNode json = JsonNodeFactory.instance.objectNode();
    boolean connected = false;
    json.put("serverTime", new Date().getTime());
    try {
      Bson ping = new BasicDBObject("ping", "1");
      database.runCommand(ping);
      connected = true;
    } catch (Exception ex) {
      json.put("storageServerException", ex.getMessage());
    }
    json.put("storageServerConnection", connected);
    return json;
  }

}
