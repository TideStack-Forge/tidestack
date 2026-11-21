package com.ouroboros.logicflow.manage.model;

import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_runtime_flow_observation_change", label = "逻辑流运行观测")
public class RuntimeFlowObservationChange {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(size = 200)
  private String flowName;

  @Field
  private Long observedAt;

  @Field(type = "Map")
  private Map<String, Object> payload;

  public String getId() { return id; }
  public RuntimeFlowObservationChange setId(String id) { this.id = id; return this; }
  public String getFlowName() { return flowName; }
  public RuntimeFlowObservationChange setFlowName(String flowName) { this.flowName = flowName; return this; }
  public Long getObservedAt() { return observedAt; }
  public RuntimeFlowObservationChange setObservedAt(Long observedAt) { this.observedAt = observedAt; return this; }
  public Map<String, Object> getPayload() { return payload; }
  public RuntimeFlowObservationChange setPayload(Map<String, Object> payload) { this.payload = payload; return this; }
}
