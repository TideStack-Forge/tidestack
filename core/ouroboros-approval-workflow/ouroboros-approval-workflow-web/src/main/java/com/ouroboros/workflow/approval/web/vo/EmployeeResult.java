package com.ouroboros.workflow.approval.web.vo;

import com.ouroboros.organizationstructure.Employee;

public class EmployeeResult {
  private String id;
  private String label;

  public EmployeeResult(Employee employee) {
    this.id = employee.getId();
    this.label = employee.getName();
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
}
