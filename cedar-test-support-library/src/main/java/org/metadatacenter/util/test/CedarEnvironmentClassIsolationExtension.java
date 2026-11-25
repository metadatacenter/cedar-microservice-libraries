package org.metadatacenter.util.test;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.metadatacenter.config.environment.CedarEnvironmentSource;

/**
 * Restores the test baseline after each JUnit test class.
 *
 * <p>Server tests install a process-global {@link CedarEnvironmentSource} override before booting
 * Dropwizard. Surefire deliberately reuses its JVM, so leaving that override installed lets one
 * class's dynamically assigned dependency ports become the next class's configuration. This
 * extension is loaded automatically from the test-support jar and closes that class-sized scope
 * even when the class fails. It restores {@link CedarTestBaseline} rather than clearing the
 * override, because a cleared override hands the next class the shell's own environment, which on
 * a workstation addresses the running stack. It also restores the shared in-memory user service's
 * users to the fixtures, so a user change one class writes does not reach the next.
 */
public final class CedarEnvironmentClassIsolationExtension implements AfterAllCallback {

  @Override
  public void afterAll(ExtensionContext context) {
    CedarTestBaseline.install();
    // A nested class ends inside its enclosing class, whose tests may still rely on the users it wrote.
    boolean nested = context.getTestClass().map(c -> c.isAnnotationPresent(Nested.class)).orElse(false);
    if (!nested) {
      TestAuthUtil.resetInMemoryUserService();
    }
  }
}
