package com.ouroboros.widget.dashboard.controller;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import io.vavr.control.Try;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.widget.Dashboard;
import com.ouroboros.widget.dashboard.service.UserDashboardService;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDashboardControllerCurrentSecurityContextTest {

  @Test
  void initAndSavePassNullIdentityWhenCurrentSecurityContextIsEmpty() {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy", "n/a"));
    CurrentSecurityContextHolder.clearDelegate();
    try {
      UserDashboardService service = mock(UserDashboardService.class);
      Dashboard expected = new Dashboard().setId("dashboard");
      when(service.getUserDashboard(isNull())).thenReturn(expected);
      when(service.updateUserDashboard(isNull(), eq(expected))).thenReturn(Try.success(null));

      UserDashboardController controller = new UserDashboardController(service);

      WebApiResult<Map<String, Dashboard>> initResult = controller.init();
      assertSame(expected, initResult.getData().get("dashboard"));

      WebApiResult<Void> saveResult = controller.save(new UserDashboardController.SaveDashboardRequest().setDashboard(expected));
      assertEquals(0, saveResult.getCode());

      verify(service).getUserDashboard(isNull());
      verify(service).updateUserDashboard(isNull(), eq(expected));
    } finally {
      SecurityContextHolder.clearContext();
      CurrentSecurityContextHolder.clearDelegate();
    }
  }
}
