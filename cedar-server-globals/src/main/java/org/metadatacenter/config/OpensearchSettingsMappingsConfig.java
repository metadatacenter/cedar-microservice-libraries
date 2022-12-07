package org.metadatacenter.config;

import java.util.Map;

public class OpensearchSettingsMappingsConfig {

  private Map<String, Object> settings;

  private OpensearchMappingsConfig mappings;

  public Map<String, Object> getSettings() {
    return settings;
  }

  public OpensearchMappingsConfig getMappings() {
    return mappings;
  }
}
