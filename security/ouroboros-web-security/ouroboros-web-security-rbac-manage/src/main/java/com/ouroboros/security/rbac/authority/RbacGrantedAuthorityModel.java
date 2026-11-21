package com.ouroboros.security.rbac.authority;

import org.apache.commons.lang3.ObjectUtils;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.DefaultAccessControlParams;
import com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;

public class RbacGrantedAuthorityModel implements OuroborosGrantedAuthority {
  private final RoleAuthorityMapEntity authorityEntity;
  private Authority authorityObject;

  public RbacGrantedAuthorityModel(RoleAuthorityMapEntity authorityEntity) {
    this.authorityEntity = authorityEntity;
  }

  @Override
  public Authority getAuthorityObject() {
    if (authorityObject == null) {
      authorityObject = AuthorityCenter.getAuthority(authorityEntity.getAuthority()).orElse(null);
    }
    return authorityObject;
  }

  @Override
  public AccessControlParams getAccessControlParams() {
    var authorityObject = getAuthorityObject();
    if (authorityObject == null) {
      return AccessControlParams.empty();
    }

    var params = authorityEntity.getAccessControlParams();
    if (ObjectUtils.isEmpty(params)) {
      return new SuperAccessControlParams(authorityObject.getAccessControlDefine());
    }
    return new DefaultAccessControlParams(authorityObject.getAccessControlDefine(), authorityEntity.getAccessControlParams());
  }

  @Override
  public String getAuthority() {
    return authorityEntity.getAuthority();
  }
}
