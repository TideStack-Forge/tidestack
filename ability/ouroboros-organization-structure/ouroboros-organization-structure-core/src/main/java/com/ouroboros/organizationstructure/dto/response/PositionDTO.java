package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.Position;

public class PositionDTO {
  private String id;
  private String name;
  private String parent;
  private String departmentName;  // 新增：部门名称显示
  private String leaderEmployeeName; // 新增：负责人姓名显示
  private Integer headcount;      // 新增：编制人数显示
  private String source;

  public static PositionDTO from(Position position) {
    PositionDTO dto = new PositionDTO();
    dto.setId(position.getId());
    dto.setName(position.getName());
    dto.setParent(position.getParent());
    dto.setHeadcount(position.getHeadcount());
    dto.setSource(position.source());
    // departmentName和leaderEmployeeName需要通过关联查询获取，暂时为空
    return dto;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getParent() { return parent; }
  public void setParent(String parent) { this.parent = parent; }
  public String getDepartmentName() { return departmentName; }
  public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
  public String getLeaderEmployeeName() { return leaderEmployeeName; }
  public void setLeaderEmployeeName(String leaderEmployeeName) { this.leaderEmployeeName = leaderEmployeeName; }
  public Integer getHeadcount() { return headcount; }
  public void setHeadcount(Integer headcount) { this.headcount = headcount; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
