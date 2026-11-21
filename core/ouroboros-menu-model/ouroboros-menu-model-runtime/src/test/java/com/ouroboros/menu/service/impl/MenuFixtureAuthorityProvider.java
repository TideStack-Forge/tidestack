package com.ouroboros.menu.service.impl;

import java.util.List;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;

/** Registers the same definition used by role grants through the canonical authority SPI. */
public final class MenuFixtureAuthorityProvider implements AuthorityProvider {
  private static final Authority DEFINITION = definition();

  private static Authority definition() {
    var authority = new BaseAuthority();
    authority.setFullName("fixture.roleManagement");
    authority.setMenuItems(List.of("systemSetting.roleManagement"));
    authority.setDelegable(true);
    return authority;
  }

  @Override
  public List<Authority> getAuthority(String name) {
    return name.equals(DEFINITION.getFullName()) ? List.of(DEFINITION) : List.of();
  }

  @Override
  public List<Authority> getAllAuthorities() { return List.of(DEFINITION); }
}
