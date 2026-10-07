package org.metadatacenter.util.test.registration.child;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;

@Path("/child")
public class ChildResource {
  @GET public String get() { return "child"; }
}
