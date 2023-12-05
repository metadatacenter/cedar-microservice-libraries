package org.metadatacenter.util.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.metadatacenter.config.MongoConnection;

import java.util.Collections;

public class MongoClientFactory {

  private final MongoConnection mongoConnection;

  private MongoClient mongoClient;

  public MongoClientFactory(MongoConnection mongoConnection) {
    this.mongoConnection = mongoConnection;
  }

  public void buildClient() {
    ServerAddress address = new ServerAddress(mongoConnection.getHost(), mongoConnection.getPort());
    MongoCredential credential = MongoCredential.createScramSha1Credential(
        mongoConnection.getUser(),
        mongoConnection.getDatabaseName(),
        mongoConnection.getPassword().toCharArray()
    );

    MongoClientSettings settings = MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(address)))
        .credential(credential)
        .build();

    this.mongoClient = MongoClients.create(settings);
  }

  public MongoClient getClient() {
    return mongoClient;
  }
}
