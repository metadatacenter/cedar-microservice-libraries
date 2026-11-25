package org.metadatacenter.util.test;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/**
 * Opens and closes a Surefire JVM's test session. On opening it installs {@link CedarTestBaseline},
 * so the first test class starts from the same environment as every later one. On closing it stops
 * the shared embedded database children.
 */
public final class CedarEmbeddedProcessSessionListener implements LauncherSessionListener {

  @Override
  public void launcherSessionOpened(LauncherSession session) {
    CedarTestBaseline.install();
  }

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    try {
      EmbeddedCedarMySql.stop();
    } finally {
      EmbeddedCedarMongo.stop();
    }
  }
}
