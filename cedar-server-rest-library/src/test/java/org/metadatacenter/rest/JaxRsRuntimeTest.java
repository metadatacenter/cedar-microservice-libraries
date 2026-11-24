package org.metadatacenter.rest;

import jakarta.ws.rs.SeBootstrap;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.Link;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.Variant;
import jakarta.ws.rs.ext.RuntimeDelegate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A shaded jar can register another implementation's delegate ahead of Jersey's. Installing a
 * stand-in first reproduces that.
 */
class JaxRsRuntimeTest {

  @Test
  void jerseyAnswersWhateverWasInstalledBefore() {
    RuntimeDelegate.setInstance(new StandInDelegate());

    JaxRsRuntime.useJerseyRuntimeDelegate();

    assertEquals(org.glassfish.jersey.internal.RuntimeDelegateImpl.class,
        RuntimeDelegate.getInstance().getClass());
  }

  private static final class StandInDelegate extends RuntimeDelegate {
    @Override
    public UriBuilder createUriBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Response.ResponseBuilder createResponseBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override
    public Variant.VariantListBuilder createVariantListBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> T createEndpoint(Application application, Class<T> endpointType) {
      throw new UnsupportedOperationException();
    }

    @Override
    public <T> HeaderDelegate<T> createHeaderDelegate(Class<T> type) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Link.Builder createLinkBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override
    public SeBootstrap.Configuration.Builder createConfigurationBuilder() {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Application application,
        SeBootstrap.Configuration configuration) {
      throw new UnsupportedOperationException();
    }

    @Override
    public CompletionStage<SeBootstrap.Instance> bootstrap(Class<? extends Application> clazz,
        SeBootstrap.Configuration configuration) {
      throw new UnsupportedOperationException();
    }

    @Override
    public EntityPart.Builder createEntityPartBuilder(String partName) {
      throw new UnsupportedOperationException();
    }
  }
}
