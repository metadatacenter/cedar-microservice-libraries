package org.metadatacenter.rest;

import jakarta.ws.rs.ext.RuntimeDelegate;

/**
 * Chooses the JAX-RS implementation behind the API's static helpers.
 *
 * <p>Every CEDAR application carries two JAX-RS implementations: Jersey for the servers' own
 * resources, and RESTEasy as the Keycloak admin client's transport. Both register a
 * {@link RuntimeDelegate}, the class behind {@code Response}, {@code UriBuilder}, {@code Link} and
 * the header delegates. The API asks {@code ServiceLoader} before anything else, so without a choice
 * made in code the first registration in a shaded jar's merged service file answers, and that order
 * follows the order of dependency declarations. RESTEasy never installs its own delegate: its client
 * builds a provider factory of its own when the installed delegate is not RESTEasy's.
 */
public final class JaxRsRuntime {

  private JaxRsRuntime() {
  }

  /** Installs Jersey's delegate, whatever was installed or registered before. */
  public static void useJerseyRuntimeDelegate() {
    RuntimeDelegate.setInstance(new org.glassfish.jersey.internal.RuntimeDelegateImpl());
  }
}
