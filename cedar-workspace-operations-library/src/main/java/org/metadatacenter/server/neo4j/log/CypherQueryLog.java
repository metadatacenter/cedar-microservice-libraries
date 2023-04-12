package org.metadatacenter.server.neo4j.log;

import java.time.Instant;
import java.util.Map;

public class CypherQueryLog {

  private String operation;
  private String originalQuery;
  private String runnableQuery;
  private Map<String, Object> parameterMap;
  private String interpolatedParamsQuery;
  private Instant start;
  private Instant end;

  public CypherQueryLog(String operation, String originalQuery, String runnableQuery,
                        Map<String, Object> parameterMap, String interpolatedParamsQuery) {
    this.operation = operation;
    this.originalQuery = originalQuery;
    this.runnableQuery = runnableQuery;
    this.parameterMap = parameterMap;
    this.interpolatedParamsQuery = interpolatedParamsQuery;
  }

  public void setStart(Instant start) {
    this.start = start;
  }

  public void setEnd(Instant end) {
    this.end = end;
  }

  public String getOperation() {
    return operation;
  }

  public String getOriginalQuery() {
    return originalQuery;
  }

  public String getRunnableQuery() {
    return runnableQuery;
  }

  public Map<String, Object> getParameterMap() {
    return parameterMap;
  }

  public String getInterpolatedParamsQuery() {
    return interpolatedParamsQuery;
  }

  public Instant getStart() {
    return start;
  }

  public Instant getEnd() {
    return end;
  }

}
