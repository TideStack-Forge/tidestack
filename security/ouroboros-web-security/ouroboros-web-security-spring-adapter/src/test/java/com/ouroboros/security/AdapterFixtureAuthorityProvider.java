package com.ouroboros.security;

import java.util.List;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;

public final class AdapterFixtureAuthorityProvider implements AuthorityProvider {
  private static final Authority DEFINITION = definition();
  private static Authority definition() {
    var authority = new BaseAuthority();
    authority.setFullName("orders.read");
    authority.setApis(List.of("GET /api/orders"));
    return authority;
  }
  public List<Authority> getAuthority(String name) {
    return DEFINITION.getFullName().equals(name) ? List.of(DEFINITION) : List.of();
  }
  public List<Authority> getAllAuthorities() { return List.of(DEFINITION); }
}
