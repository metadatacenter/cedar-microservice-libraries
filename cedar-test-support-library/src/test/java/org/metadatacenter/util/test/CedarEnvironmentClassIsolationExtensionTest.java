package org.metadatacenter.util.test;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.Extension;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

import java.util.Map;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder.request;

/**
 * The class-isolation extension is what keeps one test class's environment override from becoming the
 * next class's configuration. Calling it directly would prove only that it restores the baseline, not
 * that JUnit ever calls it, so these tests run two classes through the JUnit Platform the way Surefire
 * does, and read the extension's registration as Jupiter's autodetection reads it.
 */
class CedarEnvironmentClassIsolationExtensionTest {

  @Test
  void anOverrideOneClassInstallsDoesNotReachTheNextClass() {
    SeesTheEnvironment.sawBaseline = null;
    SeesTheEnvironment.sawBoltPort = null;

    TestExecutionSummary summary = run(InstallsAnOverride.class, SeesTheEnvironment.class);

    assertEquals(2, summary.getTestsSucceededCount(), () -> "both probe classes should run: " + summary.getFailures());
    assertNotEquals("54321", SeesTheEnvironment.sawBoltPort, "the previous class's override reached the next class");
    assertEquals(Boolean.TRUE, SeesTheEnvironment.sawBaseline, "the next class starts from the test baseline");
    assertTrue(CedarTestBaseline.isInstalled(), "the last class's override is replaced by the baseline as well");
  }

  @Test
  void theExtensionIsRegisteredForAutodetection() {
    boolean registered = ServiceLoader.load(Extension.class).stream()
        .map(ServiceLoader.Provider::type)
        .anyMatch(CedarEnvironmentClassIsolationExtension.class::equals);

    assertTrue(registered, "Jupiter loads the extension through META-INF/services/org.junit.jupiter.api.extension.Extension");
  }

  /**
   * Runs the classes in the given order on a launcher of their own. The launcher-session listeners of
   * the surrounding run are left out, because one of them stops the JVM's embedded stores when its
   * session closes. Jupiter's autodetection setting comes from the system property Surefire sets.
   */
  private static TestExecutionSummary run(Class<?>... classes) {
    LauncherDiscoveryRequest request = request()
        .selectors(java.util.Arrays.stream(classes).map(c -> selectClass(c)).toList())
        .configurationParameter("junit.jupiter.testclass.order.default", ClassOrderer.OrderAnnotation.class.getName())
        .build();
    Launcher launcher = LauncherFactory.create(LauncherConfig.builder()
        .enableLauncherSessionListenerAutoRegistration(false)
        .enableTestExecutionListenerAutoRegistration(false)
        .enableLauncherDiscoveryListenerAutoRegistration(false)
        .enablePostDiscoveryFilterAutoRegistration(false)
        .build());
    SummaryGeneratingListener listener = new SummaryGeneratingListener();
    launcher.execute(request, listener);
    return listener.getSummary();
  }

  /** Installs an override, as a server test does before it boots its application. */
  @Order(1)
  static class InstallsAnOverride {
    @BeforeAll
    static void installTheOverride() {
      CedarEnvironmentSource.setOverride(Map.of("CEDAR_NEO4J_BOLT_PORT", "54321"));
    }

    @Test
    void runs() {
      assertTrue(CedarEnvironmentSource.hasOverride());
    }
  }

  /** Records the environment it starts with. */
  @Order(2)
  static class SeesTheEnvironment {
    static volatile Boolean sawBaseline;
    static volatile String sawBoltPort;

    @Test
    void observes() {
      sawBaseline = CedarTestBaseline.isInstalled();
      sawBoltPort = CedarEnvironmentSource.get("CEDAR_NEO4J_BOLT_PORT");
    }
  }
}
