package org.metadatacenter.server.neo4j.parameter;

import com.fasterxml.jackson.databind.JsonNode;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.model.BiboStatus;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.ResourceVersion;
import org.metadatacenter.server.neo4j.cypher.CypherQueryParameter;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

import java.util.*;

public class CypherParameters {

  private Map<CypherQueryParameter, Object> map;

  public CypherParameters() {
    map = new HashMap<>();
  }

  public void put(CypherQueryParameter parameter, String value) {
    map.put(parameter, value);
  }

  public void put(CypherQueryParameter parameter, long value) {
    map.put(parameter, value);
  }

  public void put(CypherQueryParameter parameter, ResourceVersion object) {
    map.put(parameter, object.getValue());
  }

  public void put(CypherQueryParameter parameter, BiboStatus object) {
    map.put(parameter, object.getValue());
  }

  public void put(CypherQueryParameter parameter, Boolean value) {
    map.put(parameter, value);
  }

  public void put(CypherQueryParameter parameter, CedarResourceId value) {
    map.put(parameter, value);
  }

  public void put(CypherQueryParameter parameter, JsonNode value) {
    map.put(parameter, value);
  }

  public void put(ParameterPlaceholder parameter, List<String> value) {
    map.put(parameter, value);
  }

  public void put(NodeProperty parameter, List<String> value) {
    map.put(parameter, value);
  }

  public Map<String, Object> asMap() {
    Map<String, Object> r = new HashMap<>();
    for (CypherQueryParameter k : map.keySet()) {
      Object value = map.get(k);
      if (value instanceof CedarResourceId) {
        value = ((CedarResourceId) value).getId();
      }
      r.put(Neo4JUtil.escapePropertyName(k.getValue()), value);
    }
    return r;
  }

  public void addResourceTypes(Collection<CedarResourceType> resourceTypes) {
    List<String> ntl = new ArrayList<>();
    resourceTypes.forEach(cnt -> ntl.add(cnt.getValue()));
    map.put(ParameterPlaceholder.RESOURCE_TYPE_LIST, ntl);
  }
}
