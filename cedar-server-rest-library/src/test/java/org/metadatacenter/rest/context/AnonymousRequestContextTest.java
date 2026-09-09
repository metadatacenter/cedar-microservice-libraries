package org.metadatacenter.rest.context;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarNoAuthRequest;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AnonymousRequestContextTest {
  @Test void suppliedCredentialsAreNeitherResolvedNorForwardable() throws Exception {
    var request = mock(HttpServletRequest.class);
    var headers = mock(HttpHeaders.class);
    var linkedData = mock(LinkedDataUtil.class);
    when(request.getHeader("Authorization")).thenReturn("apiKey private-owner-key");
    when(headers.getHeaderString("Authorization")).thenReturn("apiKey private-owner-key");
    try (var authorization = mockStatic(Authorization.class)) {
      var context = HttpServletRequestContext.anonymous(linkedData, request, headers);
      authorization.verify(() -> Authorization.getUser(eq(linkedData), isA(CedarNoAuthRequest.class)));
      verify(request, never()).getHeader("Authorization");
      assertNull(context.getAuthorizationHeader());
      assertNull(context.getCedarUser());
      assertNull(context.getUserCreationException());
    }
  }
}
