package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

public class UpdateNodeRequest {

  private String name;
  private String leaderEmployeeId;
  private String parentId;

  /**
   * 转换为 Entity（部分更新）
   * 
   * <p>只设置请求中的字段，未设置的字段保持为 null。
   * 底层服务会只更新非 null 字段，实现部分更新。
   */
  public OrganizationNodeEntity toEntity() {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setName(name);
    entity.setLeaderEmployee(leaderEmployeeId);
    entity.setParent(parentId);
    return entity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getLeaderEmployeeId() {
    return leaderEmployeeId;
  }

  public void setLeaderEmployeeId(String leaderEmployeeId) {
    this.leaderEmployeeId = leaderEmployeeId;
  }

  public String getParentId() {
    return parentId;
  }

  public void setParentId(String parentId) {
    this.parentId = parentId;
  }
}
