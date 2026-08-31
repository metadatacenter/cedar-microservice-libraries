package org.metadatacenter.util.test;

import io.dropwizard.configuration.SubstitutingSourceProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.metadatacenter.config.CedarEnvironmentVariableSubstitutor;
import org.metadatacenter.config.ClasspathConfigurationSourceProvider;
import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Holds the configuration to the rule that lets a service start with parts of it unsubstituted.
 *
 * <p>Substitution runs in relaxed mode, so a variable the environment does not carry leaves a
 * literal {@code ${...}} behind instead of failing the load. That is deliberate: a server is granted
 * its own coordinates and the coordinates of the peers it calls, and nothing else, so most of the
 * shared configuration file is expected to reach it unsubstituted. The rule the leniency has to obey
 * is one-directional. A variable the descriptor grants a component must resolve for that component;
 * only a withheld one may remain a placeholder.
 *
 * <p>The check reads the configuration files a second time as text, through the same providers
 * {@code CedarConfig} builds from. Once the substituted YAML is bound to objects the placeholders
 * are indistinguishable from any other string, whereas the text still names the variable that
 * failed to arrive.
 */
public class CedarConfigPlaceholderTest {

  private static final List<String> CONFIG_FILES = List.of("cedar-main.yml", "cedar-search.json", "cedar-rules.json");

  private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([A-Za-z0-9_]+)}");

  private Map<String, String> previousOverride;

  static Stream<SystemComponent> serverComponents() {
    return Stream.of(SystemComponent.values()).filter(component -> component.getServerName() != null);
  }

  @BeforeEach
  public void setEnvironment() {
    previousOverride = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;
    CedarEnvironmentSource.setOverride(CedarTestEnvironment.build());
  }

  @AfterEach
  public void restoreEnvironment() {
    CedarEnvironmentSource.setOverride(previousOverride);
  }

  @Test
  public void testTheTestEnvironmentSuppliesEveryGrantedVariable() {
    CedarTestEnvironment.assertCoversEveryServerComponent();
  }

  /**
   * Requires every placeholder in the configuration files to name a declared
   * {@link CedarEnvironmentVariable}.
   *
   * <p>A name no variable carries can never be granted to anything, so no environment resolves it
   * and relaxed substitution passes it through untouched. A misspelled or renamed variable therefore
   * reaches production as a literal {@code ${...}} inside whatever host or URL it was part of, and
   * nothing on the way there objects.
   */
  @Test
  public void testEveryPlaceholderNamesADeclaredVariable() throws IOException {
    List<String> unknown = new ArrayList<>();
    for (String configFile : CONFIG_FILES) {
      Matcher matcher = PLACEHOLDER.matcher(read(configFile));
      while (matcher.find()) {
        if (CedarEnvironmentVariable.forName(matcher.group(1)) == null) {
          unknown.add(matcher.group(1) + " in " + configFile);
        }
      }
    }

    Assertions.assertTrue(unknown.isEmpty(),
        "These placeholders name no declared environment variable, so nothing can ever substitute them: " + unknown);
  }

  /**
   * Requires every variable the descriptor grants a component to reach that component's
   * configuration.
   *
   * <p>The property has two ways to break, and this test reports both. A granted variable the
   * environment leaves null reaches {@code CedarEnvironmentVariableLookup} as a present-but-empty
   * entry, and the lookup refuses to build at all; the failure arrives here as that exception,
   * named with the component that provoked it. A granted variable that resolves everywhere except
   * one spelling in the configuration leaves that one placeholder behind, which the scan catches.
   */
  @ParameterizedTest
  @MethodSource("serverComponents")
  public void testGrantedVariablesResolve(SystemComponent component) throws IOException {
    Set<String> granted = new HashSet<>();
    for (CedarEnvironmentVariable variable : CedarConfigEnvironmentDescriptor.getVariableNamesFor(component)) {
      granted.add(variable.getName());
    }

    List<String> unresolved = new ArrayList<>();
    for (String configFile : CONFIG_FILES) {
      Matcher matcher = PLACEHOLDER.matcher(substitute(component, configFile));
      while (matcher.find()) {
        if (granted.contains(matcher.group(1))) {
          unresolved.add(matcher.group(1) + " in " + configFile);
        }
      }
    }

    Assertions.assertTrue(unresolved.isEmpty(),
        component + " is granted these variables but its configuration still carries their placeholders: "
            + unresolved);
  }

  private String substitute(SystemComponent component, String configFile) throws IOException {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(component);
    SubstitutingSourceProvider provider = new SubstitutingSourceProvider(
        new ClasspathConfigurationSourceProvider(), new CedarEnvironmentVariableSubstitutor(environment));
    try (InputStream in = provider.open(configFile)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private String read(String configFile) throws IOException {
    try (InputStream in = new ClasspathConfigurationSourceProvider().open(configFile)) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
  }

}
