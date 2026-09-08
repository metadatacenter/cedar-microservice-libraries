package org.metadatacenter.util.mongo;

import com.mongodb.MongoClientSettings;
import com.mongodb.MongoCredential;
import com.mongodb.ServerAddress;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.metadatacenter.config.CedarTestRuntime;
import org.metadatacenter.config.MongoConnection;

import java.util.Collections;
import java.util.OptionalLong;
import java.util.concurrent.TimeUnit;

public class MongoClientFactory {

  private final MongoConnection mongoConnection;

  private MongoClient mongoClient;

  public MongoClientFactory(MongoConnection mongoConnection) {
    this.mongoConnection = mongoConnection;
  }

  public void buildClient() {
    this.mongoClient = MongoClients.create(buildSettings());
  }

  MongoClientSettings buildSettings() {
    ServerAddress address = new ServerAddress(mongoConnection.getHost(), mongoConnection.getPort());
    MongoCredential credential = MongoCredential.createScramSha1Credential(
        mongoConnection.getUser(),
        mongoConnection.getDatabaseName(),
        mongoConnection.getPassword().toCharArray()
    );

    OptionalLong testTimeout = CedarTestRuntime.dependencyTimeoutMillis();
    long serverSelectionTimeout = testTimeout.orElse(mongoConnection.getServerSelectionTimeoutMillis());
    long connectTimeout = testTimeout.orElse(mongoConnection.getConnectTimeoutMillis());
    long readTimeout = testTimeout.orElse(mongoConnection.getReadTimeoutMillis());
    long poolWaitTimeout = testTimeout.orElse(mongoConnection.getPoolWaitTimeoutMillis());

    return MongoClientSettings.builder()
        .applyToClusterSettings(builder -> builder.hosts(Collections.singletonList(address)))
        .applyToClusterSettings(
            builder -> builder.serverSelectionTimeout(serverSelectionTimeout, TimeUnit.MILLISECONDS))
        .applyToSocketSettings(builder -> builder
            .connectTimeout(connectTimeout, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeout, TimeUnit.MILLISECONDS))
        .applyToConnectionPoolSettings(builder -> builder
            .maxWaitTime(poolWaitTimeout, TimeUnit.MILLISECONDS)
            .maxSize(mongoConnection.getMaxPoolSize()))
        .credential(credential)
        .build();
  }

  public MongoClient getClient() {
    return mongoClient;
  }
}
