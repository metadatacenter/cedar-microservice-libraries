package org.metadatacenter.server.neo4j.cypher.sort;

import org.metadatacenter.server.folder.QuerySortField;
import org.metadatacenter.server.folder.QuerySortFieldType;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class QuerySortOptions {

  final static Map<String, QuerySortField> knownSortKeys;
  private static final QuerySortField DEFAULT_SORT_FIELD;

  static {
    knownSortKeys = new HashMap<>();
    DEFAULT_SORT_FIELD = new QuerySortField("name", NodeProperty.NAME.getValue(), QuerySortFieldType.TEXTUAL);
    addField(DEFAULT_SORT_FIELD);
    addField(
        new QuerySortField("createdOnTS", NodeProperty.CREATED_ON_TS.getValue(), QuerySortFieldType.NUMERIC));
    addField(
        new QuerySortField("lastUpdatedOnTS", NodeProperty.LAST_UPDATED_ON_TS.getValue(), QuerySortFieldType.NUMERIC));
  }

  private static void addField(QuerySortField field) {
    knownSortKeys.put(field.getName(), field);
  }

  public static QuerySortField getDefaultSortField() {
    return DEFAULT_SORT_FIELD;
  }

  public static boolean isKnownField(String name) {
    return knownSortKeys.containsKey(name);
  }

  public static Set<String> getKnownFieldNames() {
    return knownSortKeys.keySet();
  }

  public static boolean isTextual(String name) {
    return knownSortKeys.get(name) != null && knownSortKeys.get(name).isTextual();
  }

  public static String getFieldName(String name) {
    return knownSortKeys.get(name) != null ? Neo4JUtil.escapePropertyName(knownSortKeys.get(name).getFieldName()) :
        name;
  }
}
