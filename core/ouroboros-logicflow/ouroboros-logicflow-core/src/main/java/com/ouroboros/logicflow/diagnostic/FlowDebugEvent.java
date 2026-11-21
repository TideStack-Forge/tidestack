package com.ouroboros.logicflow.diagnostic;

import java.util.LinkedHashMap;
import java.util.Map;

public class FlowDebugEvent {
  private String schemaVersion = "1.0";
  private Long seq;
  private String appName;
  private String sessionId;
  private String runId;
  private String kind;
  private String modelType = "logicflow";
  private String modelId;
  private String flowName;
  private String nodeName;
  private String nodeType;
  private Long timestamp;
  private Map<String, Object> payload = new LinkedHashMap<>();

  private String captureId;
  private Long captureSeq;
  private String flowInvocationId;
  private String parentInvocationId;
  private String callerNodeName;
  private String scope;
  private String graphId;
  private FlowDebugTarget target;

  public String getCaptureId() {
    return captureId;
  }

  public void setCaptureId(String captureId) {
    this.captureId = captureId;
  }

  public Long getCaptureSeq() {
    return captureSeq;
  }

  public void setCaptureSeq(Long captureSeq) {
    this.captureSeq = captureSeq;
  }

  public String getFlowInvocationId() {
    return flowInvocationId;
  }

  public void setFlowInvocationId(String flowInvocationId) {
    this.flowInvocationId = flowInvocationId;
  }

  public String getParentInvocationId() {
    return parentInvocationId;
  }

  public void setParentInvocationId(String parentInvocationId) {
    this.parentInvocationId = parentInvocationId;
  }

  public String getCallerNodeName() {
    return callerNodeName;
  }

  public void setCallerNodeName(String callerNodeName) {
    this.callerNodeName = callerNodeName;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getGraphId() {
    return graphId;
  }

  public void setGraphId(String graphId) {
    this.graphId = graphId;
  }

  public FlowDebugTarget getTarget() {
    return target;
  }

  public void setTarget(FlowDebugTarget target) {
    this.target = target;
  }

  public String getSchemaVersion() {
    return schemaVersion;
  }

  public void setSchemaVersion(String schemaVersion) {
    this.schemaVersion = schemaVersion;
  }

  public Long getSeq() {
    return seq;
  }

  public void setSeq(Long seq) {
    this.seq = seq;
  }

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

  public String getRunId() {
    return runId;
  }

  public void setRunId(String runId) {
    this.runId = runId;
  }

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public String getModelType() {
    return modelType;
  }

  public void setModelType(String modelType) {
    this.modelType = modelType;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(String modelId) {
    this.modelId = modelId;
  }

  public String getFlowName() {
    return flowName;
  }

  public void setFlowName(String flowName) {
    this.flowName = flowName;
  }

  public String getNodeName() {
    return nodeName;
  }

  public void setNodeName(String nodeName) {
    this.nodeName = nodeName;
  }

  public String getNodeType() {
    return nodeType;
  }

  public void setNodeType(String nodeType) {
    this.nodeType = nodeType;
  }

  public Long getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload == null ? new LinkedHashMap<>() : payload;
  }
}
