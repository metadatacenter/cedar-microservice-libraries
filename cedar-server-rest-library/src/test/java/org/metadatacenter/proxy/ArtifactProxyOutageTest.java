package org.metadatacenter.proxy;

import org.junit.jupiter.api.Test;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.exception.CedarProcessingException;
import org.metadatacenter.http.CedarResponseStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.rest.context.CedarRequestContext;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** A read the artifact server never answers is an outage, and keeps the 503 an outage carries. */
class ArtifactProxyOutageTest {

  @Test
  void aReadNobodyAnswersIsAnOutageAndNotA500() throws Exception {
    HttpServer unavailable = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    unavailable.createContext("/", exchange -> exchange.close());
    unavailable.start();
    try {
      String base = "http://127.0.0.1:" + unavailable.getAddress().getPort() + "/";
      CedarConfig config = mock(CedarConfig.class, RETURNS_DEEP_STUBS);
      when(config.getServers().getArtifact().getBase()).thenReturn(base);
      when(config.getArtifactService().requireApiKey()).thenReturn("test-only-artifact-service-key-not-for-prod");
      when(config.getMicroserviceUrlUtil().getArtifact().getArtifactTypeWithId(any(CedarResourceType.class),
          any(String.class), any())).thenReturn(base + "templates/t");
      CedarRequestContext user = mock(CedarRequestContext.class);
      when(user.getAuthorizationHeader()).thenReturn("Bearer token");

      CedarProcessingException thrown = assertThrows(CedarProcessingException.class, () ->
          ArtifactProxy.executeResourceGetByProxyFromArtifactServer(config, null, CedarResourceType.TEMPLATE,
              "https://repo.metadatacenter.orgx/templates/t", Optional.empty(), user));

      assertInstanceOf(CedarDependencyUnavailableException.class, thrown);
      assertEquals(CedarResponseStatus.SERVICE_UNAVAILABLE, thrown.getErrorPack().getStatus());
    } finally {
      unavailable.stop(0);
    }
  }
}
