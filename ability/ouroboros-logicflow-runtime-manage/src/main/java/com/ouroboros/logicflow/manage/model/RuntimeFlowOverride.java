package com.ouroboros.logicflow.manage.model;

import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_runtime_flow_override", label = "逻辑流运行时管理")
public class RuntimeFlowOverride {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(size = 200)
  private String nameKey;

  @Field
  private Long version;

  @Field(type = "Map")
  private Map<String, Object> payload;

  public String getId() { return id; }
  public RuntimeFlowOverride setId(String id) { this.id = id; return this; }
  public String getNameKey() { return nameKey; }
  public RuntimeFlowOverride setNameKey(String nameKey) { this.nameKey = nameKey; return this; }
  public Long getVersion() { return version; }
  public RuntimeFlowOverride setVersion(Long version) { this.version = version; return this; }
  public Map<String, Object> getPayload() { return payload; }
  public RuntimeFlowOverride setPayload(Map<String, Object> payload) { this.payload = payload; return this; }
}
