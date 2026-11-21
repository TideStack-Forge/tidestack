package com.ouroboros.security.authentication;

import java.util.Collections;
import java.util.List;

import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

/**
 * 匿名认证信息
 *
 * @author liansz
 */
public final class AnonymousAuthentication implements OuroborosAuthentication {

  private static final AnonymousAuthentication INSTANCE = new AnonymousAuthentication();

  private AnonymousAuthentication() {
  }

  public static AnonymousAuthentication getInstance() {
    return INSTANCE;
  }


  @Override
  public List<? extends OuroborosGrantedAuthority> getAuthorities() {
    return Collections.emptyList();
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getDetails() {
    return null;
  }


  @Override
  public String getPrincipal() {
    return "anonymousUser";
  }


  @Override
  public User getUserDetail() {
    return () -> "anonymousUser";
  }


  @Override
  public List<? extends Role> getRoles() {
    return Collections.emptyList();
  }

  @Override
  public boolean isAuthenticated() {
    return false;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
  }


  @Override
  public AccessControlParams getAccessControlParams() {
    throw new com.ouroboros.security.exception.AuthorizationDeniedException("MISSING_IDENTITY");
  }


  @Override
  public AccessControlParams getAccessControlParams(String authorityName) {
    throw new com.ouroboros.security.exception.AuthorizationDeniedException("MISSING_IDENTITY");
  }

  @Override
  public String getName() {
    return null;
  }
}
