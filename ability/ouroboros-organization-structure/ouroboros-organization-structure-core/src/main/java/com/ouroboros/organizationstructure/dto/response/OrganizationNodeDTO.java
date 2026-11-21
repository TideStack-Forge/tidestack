package com.ouroboros.organizationstructure.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ouroboros.organizationstructure.OrganizationNode;

import java.util.List;

/**
 * 组织节点响应 DTO
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OrganizationNodeDTO {
  private String id;
  private String name;
  private String type;
  private String parent;
  private String leaderEmployee;
  private String adminLevel;
  private String adminLevelScope;
  private String source;
  private String icon;
  private List<OrganizationNodeDTO> children;

  public static OrganizationNodeDTO from(OrganizationNode node) {
    OrganizationNodeDTO dto = new OrganizationNodeDTO();
    dto.setId(node.getId());
    dto.setName(node.getName());
    dto.setType(node.getType());
    dto.setParent(node.getParent());
    dto.setLeaderEmployee(node.getLeaderEmployee());
    dto.setAdminLevel(node.getAdminLevel());
    dto.setSource(node.source());
    dto.setIcon(determineIcon(node.getType()));
    return dto;
  }

  private static String determineIcon(String type) {
    if ("company".equals(type)) {
      return "fa fa-building";
    } else if ("department".equals(type)) {
      return "fa fa-sitemap";
    }
    else if ("position".equals(type)) {
      return "fa fa-users";
    }
    return "fa fa-folder";
  }

  // Getters and Setters
  public String getId() { return id; }
  public void setId(String id) { this.id = id; }
  public String getName() { return name; }
  public void setName(String name) { this.name = name; }
  public String getType() { return type; }
  public void setType(String type) { this.type = type; }
  public String getParent() { return parent; }
  public void setParent(String parent) { this.parent = parent; }
  public String getLeaderEmployee() { return leaderEmployee; }
  public void setLeaderEmployee(String leaderEmployee) { this.leaderEmployee = leaderEmployee; }
  public String getAdminLevel() { return adminLevel; }
  public void setAdminLevel(String adminLevel) { this.adminLevel = adminLevel; }
  public String getAdminLevelScope() { return adminLevelScope; }
  public void setAdminLevelScope(String adminLevelScope) { this.adminLevelScope = adminLevelScope; }
  public String getSource() { return source; }
  public void setSource(String source) { this.source = source; }
  public String getIcon() { return icon; }
  public void setIcon(String icon) { this.icon = icon; }
  public List<OrganizationNodeDTO> getChildren() { return children; }
  public void setChildren(List<OrganizationNodeDTO> children) { this.children = children; }
}
