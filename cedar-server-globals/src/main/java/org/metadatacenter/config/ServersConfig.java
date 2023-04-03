package org.metadatacenter.config;

import org.metadatacenter.model.ServerName;

public class ServersConfig {

  private ServerConfig group;

  private ServerConfig messaging;

  private ServerConfig repo;

  private ResourceServerConfig resource;

  private ServerConfig schema;

  private ServerConfig submission;

  private ServerConfig artifact;

  private ServerConfig terminology;

  private UserServerConfig user;

  private ServerConfig valuerecommender;

  private ServerConfig worker;

  private ServerConfig openview;

  private ServerConfig monitor;

  private ServerConfig impex;

  public ServerConfig getGroup() {
    return group;
  }

  public ServerConfig getMessaging() {
    return messaging;
  }

  public ServerConfig getRepo() {
    return repo;
  }

  public ResourceServerConfig getResource() {
    return resource;
  }

  public ServerConfig getSchema() {
    return schema;
  }

  public ServerConfig getSubmission() {
    return submission;
  }

  public ServerConfig getArtifact() {
    return artifact;
  }

  public ServerConfig getTerminology() {
    return terminology;
  }

  public UserServerConfig getUser() {
    return user;
  }

  public ServerConfig getValuerecommender() {
    return valuerecommender;
  }

  public ServerConfig getWorker() {
    return worker;
  }

  public ServerConfig getOpenview() {
    return openview;
  }

  public ServerConfig getMonitor() {
    return monitor;
  }

  public ServerConfig getImpex() {
    return impex;
  }

  public ServerConfig get(ServerName serverName) {
    switch (serverName) {
      case GROUP:
        return group;
      case MESSAGING:
        return messaging;
      case REPO:
        return repo;
      case RESOURCE:
        return resource;
      case SCHEMA:
        return schema;
      case SUBMISSION:
        return submission;
      case ARTIFACT:
        return artifact;
      case TERMINOLOGY:
        return terminology;
      case USER:
        return user;
      case VALUERECOMMENDER:
        return valuerecommender;
      case WORKER:
        return worker;
      case OPENVIEW:
        return openview;
      case MONITOR:
        return monitor;
      case IMPEX:
        return impex;
      default:
        return null;
    }
  }
}
