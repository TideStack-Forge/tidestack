package com.ouroboros.security.authentication;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

/**
 * @author liansz
 */
public interface OuroborosAuthentication extends Principal {

  /**
   * 获取用户标识
   *
   * @return 用户标识
   */
  String getPrincipal();

  @Override
  default String getName() {
    return getPrincipal();
  }

  /** Present only for a verified internal platform access token. */
  default java.util.Optional<String> getPlatformSessionId() { return java.util.Optional.empty(); }

  Object getCredentials();

  Object getDetails();

  boolean isAuthenticated();

  void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException;

  /**
   * 获取用户详情
   *
   * @return 用户对象
   */

  User getUserDetail();

  /**
   * 获取用户角色列表
   *
   * @return 角色列表
   */

  List<? extends Role> getRoles();

  /**
   * 获取当前请求的访问控制参数
   *
   * @return 当前请求的访问控制参数
   */

  AccessControlParams getAccessControlParams();

  /** A trusted execution scope may select the published permission for data access on its own model. */
  default Optional<AccessControlParams> findScopedAccessControlParams(String dataAuthorityName) {
    return Optional.empty();
  }

  default AuthorizationDecision getAuthorizationDecision() {
    return new AuthorizationDecision.Deny(AuthorizationDecision.Reason.MISSING_DECISION_TARGET);
  }

  /**
   * 获取指定权限的访问控制参数
   *
   * @param authorityName 权限名称
   * @return 指定权限的访问控制参数
   */

  default SecurityIdentity getIdentity() {
    if (getUserDetail() == null) { return null; }
    return new DefaultSecurityIdentity(getUserDetail()) {
      @Override
      public List<Role> getRoles() { return List.copyOf(OuroborosAuthentication.this.getRoles()); }
    };
  }

  default AuthorizationDecision getAuthorizationDecision(String authorityName) {
    if (!isAuthenticated()) { return new AuthorizationDecision.Deny(AuthorizationDecision.Reason.MISSING_IDENTITY); }
    return AuthorizationEvaluator.standard().evaluate(getIdentity(), AuthorizationEvaluator.PLATFORM_API, authorityName);
  }

  default AccessControlParams getAccessControlParams(String authorityName) {
    return getAuthorizationDecision(authorityName).requirePermit();
  }

  /**
   * 获取当前请求的访问控制参数
   *
   * @return 当前请求的访问控制参数
   */
  default List<? extends OuroborosGrantedAuthority> getAuthorities() {
    if (!isAuthenticated()) { return List.of(); }
    return AuthorizationEvaluator.standard().effectiveAuthorities(getIdentity(), AuthorizationEvaluator.PLATFORM_API)
        .entrySet().stream().map(entry -> new EffectiveAuthority(
            AuthorityCenter.getAuthority(entry.getKey()).orElseThrow(), entry.getValue())).toList();
  }

  record EffectiveAuthority(com.ouroboros.security.authority.Authority definition, AccessControlParams params)
      implements OuroborosGrantedAuthority {
    @Override
    public String getAuthority() { return definition.getFullName(); }
    @Override
    public com.ouroboros.security.authority.Authority getAuthorityObject() { return definition; }
    @Override
    public AccessControlParams getAccessControlParams() { return params; }
  }
}
