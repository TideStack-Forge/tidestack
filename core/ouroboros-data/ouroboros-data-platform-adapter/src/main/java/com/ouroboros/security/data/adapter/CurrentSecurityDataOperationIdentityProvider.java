package com.ouroboros.security.data.adapter;

import java.util.Optional;

import com.ouroboros.data.model.DataOperationIdentityProvider;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

public class CurrentSecurityDataOperationIdentityProvider implements DataOperationIdentityProvider {

  @Override
  public Optional<String> findCurrentOperator() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity());
  }
}
