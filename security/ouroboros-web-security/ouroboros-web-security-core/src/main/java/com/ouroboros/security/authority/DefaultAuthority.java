package com.ouroboros.security.authority;

import com.ouroboros.model.annotation.ProCodeModel;

/**
 * @author liansz
 */
@ProCodeModel
public class DefaultAuthority extends BaseAuthority {
  public DefaultAuthority() {
  }

  public DefaultAuthority(Authority authority) {
    super(authority);
  }
}
