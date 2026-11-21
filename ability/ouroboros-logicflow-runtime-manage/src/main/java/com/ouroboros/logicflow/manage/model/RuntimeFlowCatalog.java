package com.ouroboros.logicflow.manage.model;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "sys_runtime_flow_catalog", label = "逻辑流运行时管理")
public class RuntimeFlowCatalog {
  @Field(size = 64)
  @PrimaryKey
  private String id;

  @Field(size = 64)
  private String epoch;

  @Field
  private Long revision;

  public String getId() { return id; }
  public RuntimeFlowCatalog setId(String id) { this.id = id; return this; }
  public String getEpoch() { return epoch; }
  public RuntimeFlowCatalog setEpoch(String epoch) { this.epoch = epoch; return this; }
  public Long getRevision() { return revision; }
  public RuntimeFlowCatalog setRevision(Long revision) { this.revision = revision; return this; }
}
