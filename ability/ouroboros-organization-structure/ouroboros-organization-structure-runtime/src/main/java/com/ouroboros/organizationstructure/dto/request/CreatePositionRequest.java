package com.ouroboros.organizationstructure.dto.request;

import com.ouroboros.organizationstructure.entity.PositionEntity;

public class CreatePositionRequest {

  private String name;
  private String positionKey;
  private String departmentId;
  private String description;
  private String level;
  private Integer headcount;
  private String leaderEmployee;

  public PositionEntity toEntity() {
    PositionEntity entity = new PositionEntity();
    entity.setPositionName(name);
    entity.setPositionKey(positionKey);
    entity.setParent(departmentId);
    entity.setDescription(description);
    entity.setLevel(level);
    entity.setHeadcount(headcount);
    entity.setLeaderEmployee(leaderEmployee);
    return entity;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDepartmentId() {
    return departmentId;
  }

  public String getPositionKey() {
    return positionKey;
  }

  public void setPositionKey(String positionKey) {
    this.positionKey = positionKey;
  }

  public void setDepartmentId(String departmentId) {
    this.departmentId = departmentId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getLevel() {
    return level;
  }

  public void setLevel(String level) {
    this.level = level;
  }

  public Integer getHeadcount() {
    return headcount;
  }

  public void setHeadcount(Integer headcount) {
    this.headcount = headcount;
  }

  public String getLeaderEmployee() {
    return leaderEmployee;
  }

  public void setLeaderEmployee(String leaderEmployee) {
    this.leaderEmployee = leaderEmployee;
  }
}
