package com.ouroboros.security.rbac.entity;

import java.io.Serializable;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_sys_user_role_map", label = "用户角色映射表")
public class UserRoleMapEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String user;

  @Field
  private String role;

  public UserRoleMapEntity() {
  }

  public UserRoleMapEntity(String user, String role) {
    this.user = user;
    this.role = role;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }
}
