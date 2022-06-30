package org.metadatacenter.model;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModelNodeValues {

  private ModelNodeValues() {
  }

  public static final String JSON_SCHEMA_IRI = "http://json-schema.org/draft-04/schema#";
  public static final String RDFS_IRI = "http://www.w3.org/2000/01/rdf-schema#";
  public static final String XSD_IRI = "http://www.w3.org/2001/XMLSchema#";
  public static final String PAV_IRI = "http://purl.org/pav/";
  public static final String SCHEMA_IRI = "http://schema.org/";
  public static final String OSLC_IRI = "http://open-services.net/ns/core#";
  public static final String BIBO_IRI = "http://purl.org/ontology/bibo/";
  public static final String SKOS_IRI = "http://www.w3.org/2004/02/skos/core#";

  public static final boolean TRUE = true;
  public static final boolean FALSE = false;

  public static final String XSD_STRING = "xsd:string";
  public static final String XSD_DATETIME = "xsd:dateTime";

  public static final String ONTOLOGY_CLASS = "OntologyClass";

  public static final String LD_ID = "@id";
  public static final String LD_TYPE = "@type";

  /*
   * Value set for JSON-Schema "type" property
   */
  public static final String OBJECT = "object";
  public static final String STRING = "string";
  public static final String INTEGER = "integer";
  public static final String NUMBER = "number";
  public static final String BOOLEAN = "boolean";
  public static final String ARRAY = "array";
  public static final String NULL = "null";

  /*
   * Value set for JSON-Schema "format" property
   */
  public static final String URI = "uri";
  public static final String DATE_TIME = "date-time";

  /*
   * Value set for CEDAR "temporalGranularity" property
   */
  public static final String TEMPORAL_GRANULARITY_YEAR = "year";
  public static final String TEMPORAL_GRANULARITY_MONTH = "month";
  public static final String TEMPORAL_GRANULARITY_DAY = "day";
  public static final String TEMPORAL_GRANULARITY_HOUR = "hour";
  public static final String TEMPORAL_GRANULARITY_MINUTE = "minute";
  public static final String TEMPORAL_GRANULARITY_SECOND = "second";
  public static final String TEMPORAL_GRANULARITY_DECIMALSECOND = "decimalSecond";

  public static final Set<String> TEMPORAL_GRANULARITIES = Stream
    .of(TEMPORAL_GRANULARITY_YEAR, TEMPORAL_GRANULARITY_MONTH, TEMPORAL_GRANULARITY_DAY,
      TEMPORAL_GRANULARITY_HOUR, TEMPORAL_GRANULARITY_MINUTE, TEMPORAL_GRANULARITY_SECOND,
      TEMPORAL_GRANULARITY_DECIMALSECOND).collect(Collectors.toUnmodifiableSet());

  /*
   * Value set for CEDAR "displayTimeFormat" property
   */
  public static final String TIME_FORMAT_24H = "24h";
  public static final String TIME_FORMAT_12H = "12h";

  public static final Set<String> TIME_FORMATS
    = Stream.of(TIME_FORMAT_12H, TIME_FORMAT_24H).collect(Collectors.toUnmodifiableSet());

  /*
 * Value set for CEDAR "dateType" property
 */
  public static final String SINGLE_DATE = "single-date";
  public static final String DATE_RANGE = "date-range";
}
