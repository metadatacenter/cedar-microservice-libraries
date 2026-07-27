package org.metadatacenter.cedar.util.dw;

import com.codahale.metrics.annotation.Timed;
import org.metadatacenter.config.CedarConfig;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.UriBuilder;
import java.util.HashMap;
import java.util.Map;

@Path("/")
public class CedarMicroserviceIndexResource extends CedarMicroserviceResource {

  private static String serverName;
  private static final Map<String, Object> info;
  private static final Map<String, Object> apiDocs;

  static {
    apiDocs = new HashMap<>();
    info = new HashMap<>();
    info.put("apiDocs", apiDocs);
  }

  public CedarMicroserviceIndexResource(CedarConfig cedarConfig, String serverName) {
    super(cedarConfig);
    CedarMicroserviceIndexResource.serverName = serverName;
    info.put("name", serverName);
  }

  @GET
  @Timed
  public Map<String, Object> showInfo() {
    UriBuilder baseUriBuilder = uriInfo.getBaseUriBuilder();
    apiDocs.put("swagger.json", baseUriBuilder.build().resolve("/swagger-api/swagger.json"));
    apiDocs.put("swagger-ui", baseUriBuilder.build().resolve("/api"));
    return info;
  }
}