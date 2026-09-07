package org.metadatacenter.util.test;

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

/** Closes shared embedded database children at the end of the complete Surefire JVM session. */
public final class CedarEmbeddedProcessSessionListener implements LauncherSessionListener {

  @Override
  public void launcherSessionClosed(LauncherSession session) {
    try {
      EmbeddedCedarMySql.stop();
    } finally {
      EmbeddedCedarMongo.stop();
    }
  }
}
