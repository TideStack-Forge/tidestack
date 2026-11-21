package com.ouroboros.businessmodel.dev.service.impl;

import java.lang.reflect.Method;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BusinessModelModuleTreeServiceCurrentSecurityContextTest {

  @AfterEach
  void clearSecurityContextHolder() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void getUserIdentityUsesCurrentSecurityContext() throws Exception {
    BusinessModelModuleTreeServiceImpl service = new BusinessModelModuleTreeServiceImpl();
    CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(new DefaultSecurityIdentity(() -> "seam-user")));

    Method method = BusinessModelModuleTreeServiceImpl.class.getDeclaredMethod("getUserIdentity");
    method.setAccessible(true);
    String userIdentity = (String) method.invoke(service);

    assertEquals("seam-user", userIdentity);
  }
}
