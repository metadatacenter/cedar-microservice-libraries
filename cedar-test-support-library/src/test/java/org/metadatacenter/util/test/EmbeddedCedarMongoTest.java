package org.metadatacenter.util.test;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.platform.launcher.LauncherSessionListener;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

/** Holds the embedded Mongo child process to the lifecycle its test JVM owns. */
public class EmbeddedCedarMongoTest {

  @Test
  public void closeProcessClosesTheResourceExactlyOnce() {
    AtomicInteger closeCount = new AtomicInteger();

    EmbeddedCedarMongo.closeProcess(closeCount::incrementAndGet);

    Assertions.assertEquals(1, closeCount.get());
  }

  @Test
  public void embeddedProcessTeardownIsRegisteredForTheLauncherSession() {
    boolean registered = ServiceLoader.load(LauncherSessionListener.class).stream()
        .map(ServiceLoader.Provider::type)
        .anyMatch(CedarEmbeddedProcessSessionListener.class::equals);

    Assertions.assertTrue(registered);
  }
}
