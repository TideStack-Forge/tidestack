package com.ouroboros.logicflow.dev.model;

import java.text.SimpleDateFormat;
import java.util.Date;

public class LogicFlowDebugSessionView {
  private String appName;
  private String sessionId;
  private String flowName;
  private String status;
  private Long startedAt;
  private Long lastEventAt;
  private Integer eventCount;
  private String lastNodeName;

  public String getAppName() {
    return appName;
  }

  public void setAppName(String appName) {
    this.appName = appName;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(String sessionId) {
    this.sessionId = sessionId;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public Long getStartedAt() {
    return startedAt;
  }

  public void setStartedAt(Long startedAt) {
    this.startedAt = startedAt;
  }

  public Long getLastEventAt() {
    return lastEventAt;
  }

  public void setLastEventAt(Long lastEventAt) {
    this.lastEventAt = lastEventAt;
  }

  public Integer getEventCount() {
    return eventCount;
  }

  public void setEventCount(Integer eventCount) {
    this.eventCount = eventCount;
  }

  public String getLastNodeName() {
    return lastNodeName;
  }

  public void setLastNodeName(String lastNodeName) {
    this.lastNodeName = lastNodeName;
  }

  public String getStartTime() {
    return formatTimestamp(startedAt);
  }

  public String getEndTime() {
    return formatTimestamp(lastEventAt);
  }

  public String getDuration() {
    if (startedAt == null || lastEventAt == null) {
      return "-";
    }
    long durationMs = lastEventAt - startedAt;
    if (durationMs < 1000) {
      return durationMs + "ms";
    } else if (durationMs < 60000) {
      return String.format("%.2fs", durationMs / 1000.0);
    } else {
      long minutes = durationMs / 60000;
      long seconds = (durationMs % 60000) / 1000;
      return String.format("%dm %ds", minutes, seconds);
    }
  }

  private String formatTimestamp(Long timestamp) {
    if (timestamp == null) {
      return "-";
    }
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    return sdf.format(new Date(timestamp));
  }
}
