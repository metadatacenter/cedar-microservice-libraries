package org.metadatacenter.cedar.util.dw;

import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.internal.MapPropertiesDelegate;
import java.net.URI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StrongEtagResponseFilterTest {
  private static ContainerRequest request() {
    return new ContainerRequest(URI.create("http://localhost/"), URI.create("http://localhost/groups"),
        "GET", null, new MapPropertiesDelegate());
  }

  private final StrongEtagResponseFilter filter = new StrongEtagResponseFilter();

  @Test
  void protectsStrongRevisionAndCompressedRepresentationTags() {
    for (String tag : new String[]{"1", "2--gzip", "3-resource-record"}) {
      ContainerResponse response = new ContainerResponse(request(), Response.ok().tag(tag).build());
      filter.filter(null, response);
      assertEquals(new EntityTag(tag), response.getEntityTag());
      assertTrue(CacheControl.valueOf(response.getHeaderString(HttpHeaders.CACHE_CONTROL)).isNoTransform());
    }
  }

  @Test
  void preservesExistingCachePolicyAndDoesNotDuplicateDirective() {
    ContainerResponse response = new ContainerResponse(request(),
        Response.ok().tag("7").header(HttpHeaders.CACHE_CONTROL, "private, no-store, max-age=0").build());
    filter.filter(null, response);
    String first = response.getHeaderString(HttpHeaders.CACHE_CONTROL);
    assertTrue(first.contains("no-transform"), first);
    CacheControl policy = CacheControl.valueOf(first);
    assertTrue(policy.isPrivate());
    assertTrue(policy.isNoStore());
    assertEquals(0, policy.getMaxAge());
    assertTrue(policy.isNoTransform());
    filter.filter(null, response);
    assertEquals(first, response.getHeaderString(HttpHeaders.CACHE_CONTROL));
  }

  @Test
  void leavesWeakOrAbsentValidatorsAlone() {
    for (Response original : new Response[]{Response.ok().build(), Response.ok().tag(new EntityTag("1", true)).build()}) {
      ContainerResponse response = new ContainerResponse(request(), original);
      filter.filter(null, response);
      assertNull(response.getHeaderString(HttpHeaders.CACHE_CONTROL));
      assertEquals(original.getEntityTag(), response.getEntityTag());
    }
  }
}
