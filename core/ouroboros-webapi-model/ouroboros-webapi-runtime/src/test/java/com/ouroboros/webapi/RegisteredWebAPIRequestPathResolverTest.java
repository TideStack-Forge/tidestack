package com.ouroboros.webapi;

import java.util.List;
import org.junit.jupiter.api.Test;
import com.ouroboros.webapi.util.WebAPIUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisteredWebAPIRequestPathResolverTest {
  @Test
  void usesScopeAndApplicationBasePathForCandidateAndAlreadyRegisteredPaths() throws Exception {
    var base = WebAPIUtils.class.getDeclaredField("baseApiPath");
    var suppliers = WebAPIUtils.class.getDeclaredField("webAPIBasePathSuppliers");
    base.setAccessible(true); suppliers.setAccessible(true);
    Object oldBase = base.get(null), oldSuppliers = suppliers.get(null);
    try {
      base.set(null, null);
      new WebAPIUtils(List.of(() -> "/tenant"));
      var resolver = new RegisteredWebAPIRequestPathResolver();
      assertEquals("/api/tenant/orders/{id}", resolver.resolve(SimpleWebAPIModel.builder()
          .post("/orders/{id}").scope(WebAPIScope.INTERNAL).build()));
      assertEquals("/api/tenant/orders/{id}", resolver.resolve(SimpleWebAPIModel.builder()
          .post("/api/tenant/orders/{id}").scope(WebAPIScope.INTERNAL).build()));
      assertEquals("/public-api/tenant/orders", resolver.resolve(SimpleWebAPIModel.builder()
          .post("/orders").scope(WebAPIScope.PUBLIC).build()));
    } finally {
      base.set(null, oldBase); suppliers.set(null, oldSuppliers);
    }
  }
}
