package org.metadatacenter.server.model.provenance;

import com.fasterxml.jackson.annotation.JsonValue;
import org.metadatacenter.constant.CedarConstants;

import java.lang.String;
import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Date;

public class ProvenanceTime {

  private Instant value;

  public ProvenanceTime(String s) {
    if (s != null) {
      TemporalAccessor ta = CedarConstants.xsdDateTimeFormatter.parse(s);
      value = Instant.from(ta);
    }
  }

  public ProvenanceTime(Date date) {
    if (date != null) {
      value = date.toInstant();
    }
  }

  @JsonValue
  public String toString() {
    return CedarConstants.xsdDateTimeFormatter.format(value);
  }

}
