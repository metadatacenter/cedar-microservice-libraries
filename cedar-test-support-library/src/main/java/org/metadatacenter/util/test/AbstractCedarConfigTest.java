package org.metadatacenter.util.test;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.ServerConfig;
import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;

import java.util.Map;

/**
 * Shared configuration-load test for the CEDAR microservices.
 *
 * <p>Each server subclasses this once, names its {@link SystemComponent}, and inherits the test
 * that builds the component's environment from {@link CedarTestEnvironment} and instantiates
 * {@link CedarConfig} from it. {@link CedarEnvironmentVariableProvider#getFor} narrows the
 * environment to the variables the component declares, so the surplus is inert.
 *
 * <p>The environment is installed as the whole {@link CedarEnvironmentSource} override rather than
 * merged over the process environment, so a run resolves the same variables on a developer's
 * machine and in a CI job. Subclasses can adjust it before it is applied via
 * {@link #customizeEnvironment(Map)}, and add server-specific assertions on the loaded
 * configuration via {@link #assertServerSpecificConfig(CedarConfig)}.
 *
 * <p>A server-specific assertion has to be about a value, not a section. Every server loads the
 * same {@code cedar-main.yml}, so every section is present for all of them alike and asserting
 * presence says nothing about the server under test. What differs is which variables the descriptor
 * grants, and therefore which values substituted; {@link #assertResolved(String, String)} is the
 * assertion that carries that difference.
 */
public abstract class AbstractCedarConfigTest {

  private Map<String, String> previousOverride;

  protected abstract SystemComponent getSystemComponent();

  protected void customizeEnvironment(Map<String, String> env) {
  }

  protected void assertServerSpecificConfig(CedarConfig config) {
  }

  @BeforeEach
  public void setEnvironment() {
    // The fabricated environment must not outlive this test: in a shared JVM a later test class
    // reads the active environment when it boots, so the prior override is restored afterwards
    previousOverride = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;

    Map<String, String> env = CedarTestEnvironment.build();
    customizeEnvironment(env);
    CedarEnvironmentSource.setOverride(env);
  }

  @AfterEach
  public void restoreEnvironment() {
    CedarEnvironmentSource.setOverride(previousOverride);
  }

  @Test
  public void testGetInstance() throws Exception {
    Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(getSystemComponent());
    assertGrantedVariablesSupplied(environment);

    CedarConfig instance = CedarConfig.getInstance(environment);

    Assertions.assertNotNull(instance);
    assertOwnServerCoordinates(instance);
    assertServerSpecificConfig(instance);
  }

  /**
   * Asserts that a value the server depends on came from the environment. It fails on a null or
   * blank value, and on one still carrying a {@code ${...}} placeholder, which is what a relaxed
   * substitution leaves behind when the variable never arrived.
   */
  protected void assertResolved(String label, String value) {
    Assertions.assertNotNull(value, getSystemComponent() + " loaded no value for " + label);
    Assertions.assertFalse(value.isBlank(), getSystemComponent() + " loaded a blank value for " + label);
    Assertions.assertFalse(value.contains("${"),
        getSystemComponent() + " loaded " + label + " as " + value + ", so the variable never arrived");
  }

  /**
   * Asserts that every variable the descriptor grants this component has a value.
   *
   * <p>A granted variable with no value reaches {@code CedarEnvironmentVariableLookup} as an entry
   * that is present and empty, and the lookup then refuses to build anything. This assertion runs
   * first so the failure names the variable and the component that was granted it, rather than
   * arriving as a construction error from inside Dropwizard. Booleans are exempt: the provider turns
   * an absent one into {@code false} rather than a miss, and so are optional variables, whose whole
   * point is that the component reading them carries its own default.
   */
  private void assertGrantedVariablesSupplied(Map<String, String> environment) {
    SystemComponent component = getSystemComponent();
    for (CedarEnvironmentVariable variable : CedarConfigEnvironmentDescriptor.getVariableNamesFor(component)) {
      if (variable.isBoolean() || variable.isOptional()) {
        // Booleans default to false and optional variables to whatever the component that reads them
        // uses when unset, so neither has to be supplied for the configuration to build.
        continue;
      }
      Assertions.assertNotNull(environment.get(variable.getName()),
          component + " is granted " + variable.getName() + ", which the test environment does not supply");
    }
  }

  /**
   * Asserts that the server under test loaded its own ports.
   *
   * <p>Substitution runs in relaxed mode and leaves a literal {@code ${...}} in place on a miss, while
   * the variable provider withholds any variable the component does not declare and defaults numerics
   * to {@code "0"}. A service can therefore load a configuration full of unresolved coordinates and
   * still start, which the descriptor records having happened once already.
   *
   * <p>What a server can be held to is narrow. Asserting that nothing anywhere is unsubstituted fails
   * every server, because withholding a peer's coordinates is the design rather than drift. Its own
   * base URL is no better: a server has no reason to address itself, so terminology carries a literal
   * {@code ${CEDAR_TERMINOLOGY_SERVER_HOST}} in its own entry and ten of the fifteen have no
   * {@code base} entry at all. The ports are the exception. A server binds them, so it must resolve
   * them, and a zero there is the value a withheld or unparsed variable produces.
   */
  private void assertOwnServerCoordinates(CedarConfig config) {
    ServerName serverName = getSystemComponent().getServerName();
    Assertions.assertNotNull(serverName,
        "Component " + getSystemComponent() + " names no server, so this test cannot locate its coordinates");

    ServerConfig own = config.getServers().get(serverName);
    Assertions.assertNotNull(own, serverName + " loaded no configuration for itself");

    Assertions.assertTrue(own.getHttpPort() > 0,
        serverName + " loaded httpPort " + own.getHttpPort()
            + ", which is the value a withheld or unparsed variable produces");
    Assertions.assertTrue(own.getAdminPort() > 0,
        serverName + " loaded adminPort " + own.getAdminPort()
            + ", which is the value a withheld or unparsed variable produces");
  }

}
