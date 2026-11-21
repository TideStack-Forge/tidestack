package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

import com.ouroboros.organizationstructure.Department;

public class DepartmentResult {
  private String id;
  private String label;
  private List<Object> children;

  public DepartmentResult(Department department) {
    this.id = department.getId();
    this.label = department.getName();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public List<Object> getChildren() {
    return children;
  }

  public void setChildren(List<Object> children) {
    this.children = children;
  }
}
