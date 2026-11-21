package com.ouroboros.organizationstructure.dto.response;

import com.ouroboros.organizationstructure.Company;

public class CompanyDTO {
  private String id;
  private String name;
  private String description;
  private String leaderEmployee;
  private String parent;
  private String adminLevel;
  private String source;

  public static CompanyDTO from(Company company) {
    CompanyDTO dto = new CompanyDTO();
    dto.setId(company.getId());
    dto.setName(company.getName());
    dto.setDescription(company.getDescription());
    dto.setLeaderEmployee(company.getLeaderEmployee());
    dto.setParent(company.getParent());
    dto.setAdminLevel(company.getAdminLevel());
    dto.setSource(company.source());
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
  public String getAdminLevel() { return adminLevel; }
  public void setAdminLevel(String adminLevel) { this.adminLevel = adminLevel; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
}
