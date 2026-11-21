package com.ouroboros.organizationstructure.profile.dto;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.organizationstructure.entity.EmployeeEntity;

/**
 * 员工档案归档载体：核心字段 + 扩展字段
 */
public class EmployeeProfileArchiveDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  private EmployeeEntity employee;
  private Map<String, Object> profileValues;

  public EmployeeEntity getEmployee() {
    return employee;
  }

  public void setEmployee(EmployeeEntity employee) {
    this.employee = employee;
  }

  public Map<String, Object> getProfileValues() {
    return profileValues == null ? Collections.<String, Object>emptyMap() : profileValues;
  }

  public void setProfileValues(Map<String, Object> profileValues) {
    if (profileValues == null) {
      this.profileValues = null;
      return;
    }
    this.profileValues = new LinkedHashMap<String, Object>(profileValues);
  }
}
