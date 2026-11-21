package com.ouroboros.security.rbac.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.DefaultAccessControlParams;

public class BuiltinRoleAuthorityModel implements OuroborosGrantedAuthority {

  private String authority;
  private Map<String, Object> accessControlParams = new LinkedHashMap<>();

  public void setAuthority(String authority) {
    this.authority = authority;
  }

  @JsonProperty("accessControlParams")
  public Map<String, Object> getAccessControlParamsSetting() {
    return Optional.ofNullable(accessControlParams).orElse(Collections.<String, Object>emptyMap());
  }

  /**
   * 反序列化 accessControlParams。
   * <p>必须显式标注 {@link JsonProperty}：本类的 {@link #getAccessControlParams()} 为
   * {@code @JsonIgnore} 的派生视图，Jackson 会把「accessControlParams」整个逻辑属性
   * 标记为忽略，从而连带跳过本 setter，导致角色定义读回后数据权限配置静默丢失。
   * 显式标注后，该属性按「序列化用 getAccessControlParamsSetting()、反序列化用本 setter」
   * 的拆分属性方式处理。
   */
  @JsonProperty("accessControlParams")
  public void setAccessControlParams(Map<String, Object> accessControlParams) {
    this.accessControlParams = accessControlParams == null ? new LinkedHashMap<String, Object>() : new LinkedHashMap<String, Object>(accessControlParams);
  }

  @Override
  public String getAuthority() {
    return authority;
  }

  @Override
  @JsonIgnore
  public Authority getAuthorityObject() {
    return AuthorityCenter.getAuthority(authority).orElse(null);
  }

  @Override
  @JsonIgnore
  public AccessControlParams getAccessControlParams() {
    Authority authorityObject = getAuthorityObject();
    if (authorityObject == null) {
      return AccessControlParams.empty();
    }
    return new DefaultAccessControlParams(authorityObject.getAccessControlDefine(), getAccessControlParamsSetting());
  }
}
