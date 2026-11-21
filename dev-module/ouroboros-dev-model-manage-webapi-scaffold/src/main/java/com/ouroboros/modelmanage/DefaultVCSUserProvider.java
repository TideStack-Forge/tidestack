package com.ouroboros.modelmanage;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.vcs.VCSUserProvider;

public class DefaultVCSUserProvider implements VCSUserProvider {
  @Override
  public String getCurrentUser() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(SecurityIdentity::getUserIdentity)
        .orElse(null);
  }

  @Override
  public String getCurrentUserEmail() {
    //TODO: 增加用户 Email 信息
    return "";
  }
}
