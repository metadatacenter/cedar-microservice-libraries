package org.metadatacenter.util.test;

import org.junit.jupiter.api.Test;
import org.metadatacenter.util.test.registration.RegistrationFixtures;
import org.metadatacenter.util.test.registration.RegistrationFixtures.FirstResource;
import org.metadatacenter.util.test.registration.RegistrationFixtures.ForgottenResource;
import org.metadatacenter.util.test.registration.child.ChildResource;
import org.metadatacenter.util.test.registrationextra.NeighborResource;
import org.opentest4j.AssertionFailedError;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ResourceRegistrationTest {
  private static final String PACKAGE = RegistrationFixtures.class.getPackageName();

  @Test
  void scansConcreteNestedAndSubpackageResourcesWithoutInitializingThem() throws Exception {
    var registered = ResourceRegistration.assertComplete(ResourceRegistrationTest.class, PACKAGE,
        List.of(FirstResource.class, ForgottenResource.class, ChildResource.class, NeighborResource.class));
    assertEquals(Set.of(FirstResource.class, ForgottenResource.class, ChildResource.class), Set.copyOf(registered));
  }

  @Test
  void aNewResourceMissingFromRegistrationFailsAndNamesTheOmission() {
    var failure = assertThrows(AssertionFailedError.class, () ->
        ResourceRegistration.assertComplete(ResourceRegistrationTest.class, PACKAGE,
            List.of(FirstResource.class, ChildResource.class)));
    assertTrue(failure.getMessage().contains("missing=[" + ForgottenResource.class.getName() + "]"));
  }

  @Test
  void anEmptyRegistrationCannotPass() {
    var failure = assertThrows(AssertionFailedError.class, () ->
        ResourceRegistration.assertComplete(ResourceRegistrationTest.class, PACKAGE, List.of()));
    assertTrue(failure.getMessage().contains(FirstResource.class.getName()));
    assertTrue(failure.getMessage().contains(ChildResource.class.getName()));
  }

  @Test
  void testClassesCannotSupplyTheInventoryForAProductionAnchor() {
    var failure = assertThrows(AssertionFailedError.class, () ->
        ResourceRegistration.assertComplete(ResourceRegistration.class, PACKAGE,
            List.of(FirstResource.class, ForgottenResource.class, ChildResource.class)));
    assertTrue(failure.getMessage().contains("No concrete @Path resources found"));
  }

  @Test
  void aMisspelledPackageCannotPassVacuously() {
    var failure = assertThrows(AssertionFailedError.class, () ->
        ResourceRegistration.assertComplete(ResourceRegistrationTest.class, PACKAGE + ".missing", List.of()));
    assertTrue(failure.getMessage().contains("No concrete @Path resources found"));
  }
}
