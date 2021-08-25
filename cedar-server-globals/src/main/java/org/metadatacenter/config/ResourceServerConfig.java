package org.metadatacenter.config;

public class ResourceServerConfig extends ServerConfig {

  private String regenerateSearchIndex;
  private String generateEmptySearchIndex;
  private String regenerateRulesIndex;
  private String generateEmptyRulesIndex;
  private String loadValueSetsOntology;
  private String loadValueSetsOntologyStatus;

  public String getRegenerateSearchIndex() {
    return regenerateSearchIndex;
  }

  public String getGenerateEmptySearchIndex() {
    return generateEmptySearchIndex;
  }

  public String getRegenerateRulesIndex() {
    return regenerateRulesIndex;
  }

  public String getGenerateEmptyRulesIndex() {
    return generateEmptyRulesIndex;
  }

  public String getLoadValueSetsOntology() {
    return loadValueSetsOntology;
  }

  public String getLoadValueSetsOntologyStatus() {
    return loadValueSetsOntologyStatus;
  }
}
