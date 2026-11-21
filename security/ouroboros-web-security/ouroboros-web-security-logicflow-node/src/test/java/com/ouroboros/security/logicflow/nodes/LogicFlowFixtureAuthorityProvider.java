package com.ouroboros.security.logicflow.nodes;

import java.util.List;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;

/** Exercises the same registered authority path used by API and named decisions. */
public final class LogicFlowFixtureAuthorityProvider implements AuthorityProvider {
  private static final Authority DEFINITION = definition();

  private static Authority definition() {
    var authority = new BaseAuthority();
    authority.setFullName("orders.read");
    authority.setApis(List.of("GET /api/orders"));
    authority.setDelegable(true);
    authority.setAccessControlDefine(com.ouroboros.util.Json.toBean(
        com.ouroboros.security.authority.accesscontrol.SimpleAccessControlDefine.class, """
        {"canControlFields":true,"commonFields":[
          {"name":"visibleField","label":"公开字段","type":"String"},
          {"name":"secretField","label":"受限字段","type":"String"}
        ]}
        """));
    return authority;
  }

  @Override
  public List<Authority> getAuthority(String name) {
    return name.equals(DEFINITION.getFullName()) ? List.of(DEFINITION) : List.of();
  }

  @Override
  public List<Authority> getAllAuthorities() { return List.of(DEFINITION); }
}
