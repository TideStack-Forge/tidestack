package com.ouroboros.uimodel.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.uiroute.UIRoute;
import com.ouroboros.uimodel.service.UIRouteService;

class UIRouteSpiLifecycleTest {
  @Test
  void secondaryContextCannotReplaceTheRootAndClosedRootCannotSupplyRoutes() {
    var contributor = new UIRouteServiceImpl.UIRouteWebAppInfoContributor();
    assertThrows(IllegalStateException.class, contributor::getWebAppInfo);
    try (var first = context("first")) {
      assertEquals("first", routes(contributor).getFirst().getName());
      try (var second = context("second")) {
        assertEquals("first", routes(contributor).getFirst().getName());
      }
      assertEquals("first", routes(contributor).getFirst().getName());
    }
    assertThrows(IllegalStateException.class, contributor::getWebAppInfo);
    try (var restarted = context("restarted")) {
      assertEquals("restarted", routes(contributor).getFirst().getName());
    }
    assertThrows(IllegalStateException.class, contributor::getWebAppInfo);
  }

  private List<UIRoute> routes(UIRouteServiceImpl.UIRouteWebAppInfoContributor contributor) {
    Object routes = contributor.getWebAppInfo().get("routes");
    if (!(routes instanceof List<?> values)) {
      throw new AssertionError("Missing route list");
    }
    return values.stream().map(UIRoute.class::cast).toList();
  }

  private GenericApplicationContext context(String name) {
    var context = new GenericApplicationContext();
    context.registerBean(SpringContextUtil.class);
    context.registerBean(UIRouteService.class, () -> () -> List.of(UIRoute.of(name, "/" + name)));
    context.refresh();
    return context;
  }
}
