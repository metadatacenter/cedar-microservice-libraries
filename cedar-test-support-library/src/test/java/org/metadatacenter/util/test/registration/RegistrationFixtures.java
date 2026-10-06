package org.metadatacenter.util.test.registration;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

/** Classes to discover independently of a supplied registration list. */
public final class RegistrationFixtures {
  private RegistrationFixtures() { }

  @Path("/first")
  public static class FirstResource {
    @GET public String get() { return "first"; }
  }

  @Path("/forgotten")
  public static class ForgottenResource {
    static {
      if (true) throw new AssertionError("Inventory must not initialize resource classes");
    }
    @GET public String get() { return "forgotten"; }
  }

  @Path("/abstract")
  public abstract static class AbstractResource { }

  @Path("/interface")
  public interface ResourceInterface { }
}
