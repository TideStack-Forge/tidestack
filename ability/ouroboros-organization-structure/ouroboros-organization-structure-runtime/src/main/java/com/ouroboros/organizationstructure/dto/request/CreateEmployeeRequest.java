package com.ouroboros.organizationstructure.dto.request;

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;

public class CreateEmployeeRequest {

  private static final Set<String> RESERVED_PROFILE_KEYS = reservedProfileKeys();

  private String name;
  private String gender;
  private String mobile;
  private String email;
  private LocalDate birthday;
  private String departmentId;
  private UserLinkInfo user;
  private String positionId;
  private LocalDate startTime;
  private final Map<String, Object> profileValues = new LinkedHashMap<String, Object>();

  public EmployeeEntity toEntity() {
    EmployeeEntity entity = new EmployeeEntity();
    entity.setName(name);
    entity.setGender(gender);
    entity.setMobile(mobile);
    entity.setEmail(email);
    entity.setBirthday(birthday != null ? birthday.toString() : null);
    return entity;
  }

  @JsonAnySetter
  public void putProfileValue(String key, Object value) {
    if (key == null || RESERVED_PROFILE_KEYS.contains(key)) {
      return;
    }
    profileValues.put(key, value);
  }

  @JsonAnyGetter
  public Map<String, Object> getProfileValues() {
    return Collections.unmodifiableMap(profileValues);
  }

  @JsonIgnore
  public Map<String, Object> toProfileValues() {
    return new LinkedHashMap<String, Object>(profileValues);
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getGender() {
    return gender;
  }

  public void setGender(String gender) {
    this.gender = gender;
  }

  public LocalDate getBirthday() {
    return birthday;
  }

  public void setBirthday(LocalDate birthday) {
    this.birthday = birthday;
  }

  public String getDepartmentId() {
    return departmentId;
  }

  public void setDepartmentId(String departmentId) {
    this.departmentId = departmentId;
  }

  public UserLinkInfo getUser() {
    return user;
  }

  public void setUser(UserLinkInfo user) {
    this.user = user;
  }

  public String getPositionId() {
    return positionId;
  }

  public void setPositionId(String positionId) {
    this.positionId = positionId;
  }

  public LocalDate getStartTime() {
    return startTime;
  }

  public void setStartTime(LocalDate startTime) {
    this.startTime = startTime;
  }

  public String getMobile() {
    return mobile;
  }

  public void setMobile(String mobile) {
    this.mobile = mobile;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  private static Set<String> reservedProfileKeys() {
    Set<String> keys = new HashSet<String>();
    Collections.addAll(keys,
        "id",
        "name",
        "gender",
        "mobile",
        "email",
        "birthday",
        "departmentId",
        "user",
        "positionId",
        "startTime");
    return Collections.unmodifiableSet(keys);
  }
}
