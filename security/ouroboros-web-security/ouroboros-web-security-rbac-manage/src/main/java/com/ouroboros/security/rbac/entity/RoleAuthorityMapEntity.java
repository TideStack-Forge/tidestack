package com.ouroboros.security.rbac.entity;

import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

@Model(rawName = "t_sys_role_authority_map", label = "角色权限映射表")
public class RoleAuthorityMapEntity implements Serializable {

  private static final long serialVersionUID = 1L;

  @Field
  @PrimaryKey(generator = "snowflake")
  private String id;

  @Field
  private String role;

  @Field
  private String authority;

  @Field(nullable = true)
  private Map<String, Object> accessControlParams;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getAuthority() {
    return authority;
  }

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  public Map<String, Object> getAccessControlParams() {
    return Optional.ofNullable(accessControlParams).orElse(Collections.emptyMap());
  }

  public void setAccessControlParams(Map<String, Object> accessControlParams) {
    this.accessControlParams = accessControlParams;
  }
}
