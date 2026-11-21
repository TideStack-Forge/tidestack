package com.ouroboros.organizationstructure.profile.entity;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;
import com.ouroboros.data.annotation.AuditFields;

/**
 * 员工档案扩展值存储实体
 */
@Model(rawName = "t_sys_employee_profile_value", label = "员工档案扩展值表")
@AuditFields
public class EmployeeProfileValueEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey
  private String employeeId;

  @Field(type = "Map", nullable = true)
  private Map<String, Object> profileValues;

  public String getEmployeeId() {
    return employeeId;
  }

  public void setEmployeeId(String employeeId) {
    this.employeeId = employeeId;
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
