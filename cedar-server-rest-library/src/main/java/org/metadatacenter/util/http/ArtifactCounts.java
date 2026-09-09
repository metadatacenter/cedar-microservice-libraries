package org.metadatacenter.util.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.metadatacenter.exception.CedarDependencyUnavailableException;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;

/** Actual document-store totals, independent of the graph and search index. */
public final class ArtifactCounts {
  public static final String PATH = "monitor/artifact-counts";
  public final long field;
  public final long element;
  public final long template;
  public final long instance;

  public ArtifactCounts(long field, long element, long template, long instance) {
    if (field < 0 || element < 0 || template < 0 || instance < 0) {
      throw new IllegalArgumentException("Artifact counts must be nonnegative");
    }
    this.field = field;
    this.element = element;
    this.template = template;
    this.instance = instance;
  }

  /** Never turn a failed, missing or malformed downstream count into a successful zero. */
  public static ArtifactCounts read(ClassicHttpResponse response) throws CedarDependencyUnavailableException {
    try {
      if (response.getCode() != 200 || response.getEntity() == null) {
        throw new IOException("Missing artifact counts");
      }
      JsonNode body = JsonMapper.STRICT_MAPPER.readTree(EntityUtils.toByteArray(response.getEntity()));
      return new ArtifactCounts(count(body, "field"), count(body, "element"),
          count(body, "template"), count(body, "instance"));
    } catch (IOException e) {
      throw new CedarDependencyUnavailableException("Artifact counts are unavailable", e);
    }
  }

  private static long count(JsonNode body, String name) throws IOException {
    JsonNode value = body == null ? null : body.get(name);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() < 0) {
      throw new IOException("Invalid artifact count");
    }
    return value.longValue();
  }
}
