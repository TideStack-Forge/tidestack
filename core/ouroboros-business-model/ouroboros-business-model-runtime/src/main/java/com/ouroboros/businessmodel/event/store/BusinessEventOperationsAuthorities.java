package com.ouroboros.businessmodel.event.store;

import java.util.List;

import com.ouroboros.businessmodel.model.BusinessModelAuthority;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;

/** Explicit operations permissions; event production and reading do not grant redrive. */
public class BusinessEventOperationsAuthorities implements AuthorityProvider {

  @Override
  public List<Authority> getAuthority(String fullName) {
    return getAllAuthorities().stream().filter(authority -> authority.getFullName().equals(fullName)).toList();
  }

  @Override
  public List<Authority> getAllAuthorities() {
    var inspect = new BusinessModelAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.INSPECT_DELIVERIES, "查看业务事件交付诊断");
    inspect.addApi("GET /api/business-events/*/deliveries");
    var redrive = new BusinessModelAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES, "重驱暂停的业务事件交付");
    redrive.addApi("POST /api/business-event-deliveries/*/redrive");
    return List.of(inspect, redrive);
  }
}
