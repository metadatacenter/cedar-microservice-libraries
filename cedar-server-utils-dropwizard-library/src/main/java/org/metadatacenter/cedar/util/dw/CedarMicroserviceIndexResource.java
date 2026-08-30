package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.config.CedarConfig;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/")
public class CedarMicroserviceIndexResource extends CedarMicroserviceResource {

  /** Where the Swagger UI is served, by nginx rather than by the service itself. */
  static final String SWAGGER_UI_PATH = "/api";

  private static String serverName;
  private static final Map<String, Object> info;

  static {
    info = new LinkedHashMap<>();
  }

  public CedarMicroserviceIndexResource(CedarConfig cedarConfig, String serverName) {
    super(cedarConfig);
    CedarMicroserviceIndexResource.serverName = serverName;
    info.put("name", serverName);
  }

  @GET
  @Timed
  public Map<String, Object> showInfo() {
    Map<String, Object> response = new LinkedHashMap<>(info);
    Map<String, Object> apiDocs =
        apiDocs(uriInfo.getBaseUriBuilder().build(), CedarMicroserviceApplication.shipsApiSpec());
    if (!apiDocs.isEmpty()) {
      response.put("apiDocs", apiDocs);
    }
    return response;
  }

  /**
   * The documentation links for a service, or nothing where it has no documentation.
   *
   * <p>Both links were advertised from the root of every service, and ten of them serve neither, so
   * a caller following either one arrived at a 404. Gating on the same fact the asset bundle is
   * gated on keeps the two from disagreeing: a service either has a spec and offers both links, or
   * has none and offers neither.
   *
   * <p>Returns a fresh map per call. The links resolve against the requesting base URI, so they
   * differ between callers reaching the same service by different names, and the map they went into
   * used to be a static one rewritten on every request.
   */
  static Map<String, Object> apiDocs(URI baseUri, boolean shipsApiSpec) {
    if (!shipsApiSpec) {
      return Map.of();
    }
    Map<String, Object> apiDocs = new LinkedHashMap<>();
    apiDocs.put("swagger.json", baseUri.resolve(CedarMicroserviceApplication.API_SPEC_PATH));
    apiDocs.put("swagger-ui", baseUri.resolve(SWAGGER_UI_PATH));
    return apiDocs;
  }
}
