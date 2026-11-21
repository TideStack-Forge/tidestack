package com.ouroboros.businessmodel.event.store;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.*;

class BusinessEventOperationsAuthoritiesTest {
  @Test
  void registeredOperationsAuthoritiesGrantTheirOwnRouteAndNeverEachOther() {
    var provider = SPI.getSortedServiceStream(AuthorityProvider.class)
        .filter(BusinessEventOperationsAuthorities.class::isInstance).findFirst().orElseThrow();
    assertEquals(2, provider.getAllAuthorities().size());
    var inspect = provider.getAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.INSPECT_DELIVERIES).get(0);
    var redrive = provider.getAuthority(com.ouroboros.businessmodel.event.BusinessEventAuthorities.REDRIVE_DELIVERIES).get(0);
    assertTrue(matches(inspect.getApis(), "GET", "/api/business-events/event-1/deliveries"));
    assertFalse(matches(inspect.getApis(), "POST", "/api/business-event-deliveries/delivery-1/redrive"));
    assertTrue(matches(redrive.getApis(), "POST", "/api/business-event-deliveries/delivery-1/redrive"));
    assertFalse(matches(redrive.getApis(), "GET", "/api/business-events/event-1/deliveries"));
    assertTrue(provider.getAuthority("sales.Order.events.submitted.emit").isEmpty());
  }

  private static boolean matches(List<String> apis, String method, String path) {
    return apis.stream().anyMatch(api -> api.startsWith(method + " ")
        && new PathPatternParser().parse(api.substring(api.indexOf(' ') + 1)).matches(PathContainer.parsePath(path)));
  }
}
