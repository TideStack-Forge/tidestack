package com.ouroboros.workflow.approval.web.user;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.workflow.approval.CurrentUserSupplier;

public class CurrentSecurityUserSupplier implements CurrentUserSupplier {
  @Override
  public String getIdentity() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(SecurityIdentity::getUserIdentity)
        .orElse(null);
  }
}
