package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

public class UpdateDepartmentRequest {

  private String name;
  private String description;
  private String leaderEmployee;
  private String adminLevel;
  private String parent;

  /**
   * 转换为 Entity（部分更新）
   * 
   * <p>只设置请求中的字段，未设置的字段保持为 null。
   * 底层服务会只更新非 null 字段，实现部分更新。
   */
  public OrganizationNodeEntity toEntity() {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setName(name);
    entity.setDescription(description);
    entity.setLeaderEmployee(leaderEmployee);
    entity.setAdminLevel(adminLevel);
    entity.setParent(parent);
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

  public String getLeaderEmployee() {
    return leaderEmployee;
  }

  public void setLeaderEmployee(String leaderEmployee) {
    this.leaderEmployee = leaderEmployee;
  }

  public String getAdminLevel() {
    return adminLevel;
  }

  public void setAdminLevel(String adminLevel) {
    this.adminLevel = adminLevel;
  }

  public String getParent() {
    return parent;
  }

  public void setParent(String parent) {
    this.parent = parent;
  }
}
