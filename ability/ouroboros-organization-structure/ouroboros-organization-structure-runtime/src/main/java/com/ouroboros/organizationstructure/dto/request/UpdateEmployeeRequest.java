package com.ouroboros.organizationstructure.dto.request;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ouroboros.organizationstructure.entity.EmployeeEntity;

public class UpdateEmployeeRequest {

  private static final Set<String> RESERVED_PROFILE_KEYS = reservedProfileKeys();

  private String name;
  private String gender;
  private String mobile;
  private String email;
  private String birthday;
  private String user;
  private final Map<String, Object> profileValues = new LinkedHashMap<String, Object>();

  public EmployeeEntity toEntity() {
    EmployeeEntity entity = new EmployeeEntity();
    entity.setName(name);
    entity.setGender(gender);
    entity.setMobile(mobile);
    entity.setEmail(email);
    entity.setBirthday(birthday);
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

  public String getBirthday() {
    return birthday;
  }

  public void setBirthday(String birthday) {
    this.birthday = birthday;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
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
        "user");
    return Collections.unmodifiableSet(keys);
  }
}
