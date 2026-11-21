package com.ouroboros.security.rbac.authority;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams;

public class AdministratorGrantedAuthorityModel implements OuroborosGrantedAuthority {
  private final String authority;
  private final Authority authorityObject;
  private final AccessControlParams accessControlParams;

  public AdministratorGrantedAuthorityModel(Authority authorityObject) {
    this.authority = authorityObject.getFullName();
    this.authorityObject = authorityObject;
    this.accessControlParams = new SuperAccessControlParams(authorityObject.getAccessControlDefine());
  }

  @Override
  public String getAuthority() {
    return authority;
  }

  @Override
  public Authority getAuthorityObject() {
    return authorityObject;
  }

  @Override
  public AccessControlParams getAccessControlParams() {
    return accessControlParams;
  }
}
