package org.metadatacenter.util.test;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

/**
 * Restores the real process environment after each JUnit test class.
 *
 * <p>Server tests install a process-global {@link CedarEnvironmentSource} override before booting
 * Dropwizard. Surefire deliberately reuses its JVM, so leaving that override installed lets one
 * class's dynamically assigned dependency ports become the next class's configuration. This
 * extension is loaded automatically from the test-support jar and closes that class-sized scope
 * even when the class fails.
 */
public final class CedarEnvironmentClassIsolationExtension implements AfterAllCallback {

  @Override
  public void afterAll(ExtensionContext context) {
    CedarEnvironmentSource.clearOverride();
  }
}
