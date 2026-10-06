package org.metadatacenter.util.test;

import com.google.common.reflect.ClassPath;
import jakarta.ws.rs.Path;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/** Compares compiled business resources with registration independently of the application's wiring. */
public final class ResourceRegistration {
  private ResourceRegistration() { }

  /**
   * Requires every concrete {@code @Path} class in the package (including subpackages and nested
   * classes) to be registered, and returns the registered classes for route probes.
   *
   * <p>The application class anchors the production code location: test fixtures and dependencies
   * sharing the package do not become intended application resources. Guava scans directories and
   * jars; loading classes for inspection does not initialize them or construct resource instances.
   * Abstract bases, interfaces and unannotated helpers are not resources. There are no implicit
   * exceptions for conditional resources: callers must choose a package whose concrete resources
   * are all intended to be active in the configuration being tested.
   */
  public static List<Class<?>> assertComplete(Class<?> applicationClass, String resourcePackage,
                                              Iterable<?> registeredComponents) throws IOException {
    Assertions.assertFalse(resourcePackage.isBlank(), "Choose an explicit business-resource package");
    var source = applicationClass.getProtectionDomain().getCodeSource();
    Assertions.assertNotNull(source, "Cannot locate the application's compiled classes");
    Set<String> declared = new TreeSet<>();
    for (ClassPath.ClassInfo info : ClassPath.from(applicationClass.getClassLoader()).getAllClasses()) {
      if (!info.getName().startsWith(resourcePackage + ".")) {
        continue;
      }
      Class<?> type = info.load();
      var typeSource = type.getProtectionDomain().getCodeSource();
      if (typeSource != null && source.getLocation().equals(typeSource.getLocation())
          && !type.isInterface() && !Modifier.isAbstract(type.getModifiers()) && !type.isSynthetic()
          && type.isAnnotationPresent(Path.class)) {
        declared.add(type.getName());
      }
    }
    Assertions.assertFalse(declared.isEmpty(),
        "No concrete @Path resources found in " + resourcePackage + " at " + source.getLocation());

    List<Class<?>> registered = RouteSurface.registeredResourceClasses(registeredComponents, resourcePackage);
    Set<String> actual = registered.stream().map(Class::getName).collect(Collectors.toCollection(TreeSet::new));
    Set<String> missing = new TreeSet<>(declared);
    missing.removeAll(actual);
    Set<String> unexpected = new TreeSet<>(actual);
    unexpected.removeAll(declared);
    Assertions.assertEquals(declared, actual,
        "Business resource registration differs from compiled resources; missing=" + missing
            + "; unexpected=" + unexpected);
    return registered;
  }
}
