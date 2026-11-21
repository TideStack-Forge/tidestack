package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;

public class CreateDepartmentRequest {
  private String name;
  private String description;
  private String parentId;
  private String leaderEmployeeId;
  private String adminLevel;
  private boolean withDefaultPosition = false;

  public OrganizationNodeEntity toEntity() {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setName(name);
    entity.setDescription(description);
    // 空字符串转换为null，避免数据库存储空字符串
    entity.setParent(parentId != null && !parentId.trim().isEmpty() ? parentId : null);
    entity.setLeaderEmployee(leaderEmployeeId);
    entity.setAdminLevel(adminLevel);
    entity.setType("DEPARTMENT");
    return entity;
  }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getParentId() { return parentId; }
  public void setParentId(String parentId) { this.parentId = parentId; }
  public String getLeaderEmployeeId() { return leaderEmployeeId; }
  public void setLeaderEmployeeId(String leaderEmployeeId) { this.leaderEmployeeId = leaderEmployeeId; }
  public String getAdminLevel() { return adminLevel; }
  public void setAdminLevel(String adminLevel) { this.adminLevel = adminLevel; }
  public boolean isWithDefaultPosition() { return withDefaultPosition; }
  public void setWithDefaultPosition(boolean withDefaultPosition) { this.withDefaultPosition = withDefaultPosition; }
}
