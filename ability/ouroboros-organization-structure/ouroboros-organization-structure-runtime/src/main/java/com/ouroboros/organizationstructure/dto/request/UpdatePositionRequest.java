package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.entity.PositionEntity;

public class UpdatePositionRequest {

  private String name;
  private String description;
  private Integer headcount;

  /**
   * 转换为 Entity（部分更新）
   * 
   * <p>只设置请求中的字段，未设置的字段保持为 null。
   * 底层服务会只更新非 null 字段，实现部分更新。
   */
  public PositionEntity toEntity() {
    PositionEntity entity = new PositionEntity();
    entity.setPositionName(name);
    entity.setDescription(description);
    entity.setHeadcount(headcount);
    return entity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getHeadcount() {
    return headcount;
  }

  public void setHeadcount(Integer headcount) {
    this.headcount = headcount;
  }
}
