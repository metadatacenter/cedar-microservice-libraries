package org.metadatacenter.cedar.util.dw;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.Configuration;

import java.util.Optional;

public class CedarMicroserviceConfiguration extends Configuration {

  private Integer testPort;

  @JsonProperty("testPort")
  public Optional<Integer> getTestPort() {
    return Optional.ofNullable(this.testPort);
  }
}
