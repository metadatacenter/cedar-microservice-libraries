package org.metadatacenter.server.logging.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.logging.AppLogger;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AppLogMessage {

  private String globalRequestId;
  private String localRequestId;
  private SystemComponent systemComponent;
  private AppLogType type;
  private AppLogSubType subType;
  private Instant logTime;
  private Instant startTime;
  private Instant endTime;
  private Duration duration;
  private Map<String, Object> parameters = new HashMap<>();

  public AppLogMessage() {
  }

  public AppLogMessage(SystemComponent systemComponent, AppLogType type, AppLogSubType subType, String globalRequestId,
                       String localRequestId) {
    this.logTime = Instant.now();
    this.systemComponent = systemComponent;
    this.globalRequestId = globalRequestId;
    this.localRequestId = localRequestId;
    this.type = type;
    this.subType = subType;
  }

  public AppLogMessage param(AppLogParam param, Object value) {
    this.parameters.put(param.getValue(), value);
    return this;
  }

  public void setStartTime(Instant startTime) {
    this.startTime = startTime;
  }

  public void setEndTime(Instant endTime) {
    this.endTime = endTime;
  }

  public void setDuration(Duration duration) {
    this.duration = duration;
  }

  public void enqueue() {
    AppLogger.enqueue(this);
  }

  public String getGlobalRequestId() {
    return globalRequestId;
  }

  public String getLocalRequestId() {
    return localRequestId;
  }

  public SystemComponent getSystemComponent() {
    return systemComponent;
  }

  public AppLogType getType() {
    return type;
  }

  public AppLogSubType getSubType() {
    return subType;
  }

  public Instant getLogTime() {
    return logTime;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public Duration getDuration() {
    return duration;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  @JsonIgnore
  public String getParamAsString(AppLogParam paramName) {
    return (String) parameters.get(paramName.getValue());
  }

  @JsonIgnore
  public Instant getParamAsInstant(AppLogParam paramName) {
    return (Instant) parameters.get(paramName.getValue());
  }

  @JsonIgnore
  public Map<String, Object> getParamAsMap(AppLogParam paramName) {
    return (Map<String, Object>) parameters.get(paramName.getValue());
  }

  @JsonIgnore
  public int getParamAsInt(AppLogParam paramName) {
    Object o = parameters.get(paramName.getValue());
    if (o == null) {
      return 0;
    } else {
      return (Integer) o;
    }
  }

}
