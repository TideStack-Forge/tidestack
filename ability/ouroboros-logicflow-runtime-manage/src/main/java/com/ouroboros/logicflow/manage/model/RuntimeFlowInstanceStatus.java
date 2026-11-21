package com.ouroboros.logicflow.manage.model;

import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_runtime_flow_instance", label = "逻辑流运行时管理")
public class RuntimeFlowInstanceStatus {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(type = "Map")
  private Map<String, Object> payload;

  public String getId() { return id; }
  public RuntimeFlowInstanceStatus setId(String id) { this.id = id; return this; }
  public Map<String, Object> getPayload() { return payload; }
  public RuntimeFlowInstanceStatus setPayload(Map<String, Object> payload) { this.payload = payload; return this; }
}
