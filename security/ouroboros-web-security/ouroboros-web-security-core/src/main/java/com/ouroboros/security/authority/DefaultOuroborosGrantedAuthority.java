package com.ouroboros.security.authority;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;

/**
 * @author liansz
 */
public class DefaultOuroborosGrantedAuthority implements OuroborosGrantedAuthority {
  OuroborosGrantedAuthority authority;

  public DefaultOuroborosGrantedAuthority(OuroborosGrantedAuthority authority) {
    this.authority = authority;
  }

  @Override
  public String getAuthority() {
    return authority.getAuthority();
  }

  @Override
  public Authority getAuthorityObject() {
    return authority.getAuthorityObject();
  }

  @Override
  public AccessControlParams getAccessControlParams() {
    return authority.getAccessControlParams();
  }
}
