package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;

public class DepartmentDTO {
  private String id;
  private String name;
  private String description;
  private String leaderEmployee;
  private String parent;
  private String companyName;
  private String adminLevel;
  private String source;

  public static DepartmentDTO from(Department department) {
    DepartmentDTO dto = new DepartmentDTO();
    dto.setId(department.getId());
    dto.setName(department.getName());
    dto.setDescription(department.getDescription());
    dto.setLeaderEmployee(department.getLeaderEmployee());
    dto.setParent(department.getParent());
    dto.setAdminLevel(department.getAdminLevel());
    dto.setSource(department.source());
    
    // Set company name from company detail
    Company company = department.getCompanyDetail();
    if (company != null) {
      dto.setCompanyName(company.getName());
    }
    
    return dto;
  }

  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getDescription() { return description; }
  public void setDescription(String description) { this.description = description; }
  public String getLeaderEmployee() { return leaderEmployee; }
  public void setLeaderEmployee(String leaderEmployee) { this.leaderEmployee = leaderEmployee; }
  public String getParent() { return parent; }
  public void setParent(String parent) { this.parent = parent; }
  public String getCompanyName() { return companyName; }
  public void setCompanyName(String companyName) { this.companyName = companyName; }
  public String getAdminLevel() { return adminLevel; }
  public void setAdminLevel(String adminLevel) { this.adminLevel = adminLevel; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
