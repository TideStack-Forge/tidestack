package com.ouroboros.logicflow.manage.model;

import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_runtime_flow_change", label = "逻辑流运行时管理")
public class RuntimeFlowChange {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(size = 200, nullable = true)
  private String nameKey;

  @Field(nullable = true)
  private String committedAt;

  public String getNameKey() { return nameKey; }
  public RuntimeFlowChange setNameKey(String value) { nameKey = value; return this; }
  public String getCommittedAt() { return committedAt; }
  public RuntimeFlowChange setCommittedAt(String value) { committedAt = value; return this; }

  @Field(type = "Map")
  private Map<String, Object> payload;

  public String getId() { return id; }
  public RuntimeFlowChange setId(String id) { this.id = id; return this; }
  public Map<String, Object> getPayload() { return payload; }
  public RuntimeFlowChange setPayload(Map<String, Object> payload) { this.payload = payload; return this; }
}
