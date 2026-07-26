package org.metadatacenter.cedar.util.dw;

import io.dropwizard.jersey.DropwizardResourceConfig;
import org.glassfish.jersey.InjectionManagerProvider;
import org.glassfish.jersey.internal.inject.InjectionManager;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Feature;
import javax.ws.rs.core.FeatureContext;
import java.lang.reflect.Field;

/**
 * Restores field-level {@code @Context} injection for components registered as instances.
 *
 * CEDAR registers every resource and exception mapper as a pre-built instance, with the
 * request-scoped {@code @Context} fields declared on shared base classes. The Jersey version
 * shipped with Dropwizard 2 no longer injects such instances: Dropwizard wraps each resource
 * instance in a {@link DropwizardResourceConfig.SpecificBinder} that hands the instance to
 * Jersey on demand, bypassing injection entirely. This feature walks the registered instances
 * at startup, unwraps the binder-held resources, and injects each component explicitly. The
 * injected values are thread-safe per-request proxies, because Jersey binds the request-scoped
 * types as proxiable.
 */
class InstanceContextInjectionFeature implements Feature {

  private static final Logger log = LoggerFactory.getLogger(InstanceContextInjectionFeature.class);

  private final ResourceConfig resourceConfig;

  InstanceContextInjectionFeature(ResourceConfig resourceConfig) {
    this.resourceConfig = resourceConfig;
  }

  @Override
  public boolean configure(FeatureContext context) {
    InjectionManager injectionManager = InjectionManagerProvider.getInjectionManager(context);
    for (Object instance : resourceConfig.getInstances()) {
      if (instance == this) {
        continue;
      }
      Object target = instance;
      if (instance instanceof DropwizardResourceConfig.SpecificBinder) {
        target = boundObject((DropwizardResourceConfig.SpecificBinder) instance);
        if (target == null) {
          continue;
        }
      } else if (instance instanceof org.glassfish.jersey.internal.inject.Binder
          || instance instanceof org.glassfish.hk2.utilities.Binder) {
        // Infrastructure binders configure bindings; they have nothing to inject.
        continue;
      }
      injectionManager.inject(target);
    }
    return true;
  }

  private Object boundObject(DropwizardResourceConfig.SpecificBinder binder) {
    try {
      Field objectField = DropwizardResourceConfig.SpecificBinder.class.getDeclaredField("object");
      objectField.setAccessible(true);
      return objectField.get(binder);
    } catch (ReflectiveOperationException e) {
      log.error("Could not unwrap the resource instance held by " + binder.getClass().getName(), e);
      return null;
    }
  }
}
