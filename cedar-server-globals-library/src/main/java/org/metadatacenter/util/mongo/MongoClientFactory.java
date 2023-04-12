package org.metadatacenter.util.mongo;

import com.mongodb.MongoClient;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import org.metadatacenter.config.MongoConnection;

import java.util.ArrayList;
import java.util.List;

public class MongoClientFactory {

  private final MongoConnection mongoConnection;

  private MongoClient mongoClient;

  public MongoClientFactory(MongoConnection mongoConnection) {
    this.mongoConnection = mongoConnection;
  }

  public void buildClient() {
    ServerAddress address = new ServerAddress(mongoConnection.getHost(), mongoConnection.getPort());
    List<MongoCredential> credentials = new ArrayList<>();
    credentials.add(
        MongoCredential.createScramSha1Credential(
            mongoConnection.getUser(),
            mongoConnection.getDatabaseName(),
            mongoConnection.getPassword().toCharArray()
        )
    );
    this.mongoClient = new MongoClient(address, credentials);
  }

  public MongoClient getClient() {
    return mongoClient;
  }
}
