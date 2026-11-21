package com.ouroboros.notification.service.impl;

import java.util.List;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.authority.BaseAuthority;

/** Definitions for the direct behavior providers used by notification integration tests. */
public final class NotificationFixtureAuthorityProvider implements AuthorityProvider {
  private static final List<Authority> DEFINITIONS = List.of("sales.Order.events.submitted.emit",
      "sales.Order.events.submitted.read", "cases.Document.workflow", "cases.Document.events.workflowApproved.emit")
      .stream().<Authority>map(name -> {
        var definition = new BaseAuthority(); definition.setFullName(name); return definition;
      }).toList();

  @Override
  public List<Authority> getAuthority(String name) {
    return DEFINITIONS.stream().filter(definition -> name.equals(definition.getFullName())).toList();
  }

  @Override
  public List<Authority> getAllAuthorities() { return DEFINITIONS; }
}
