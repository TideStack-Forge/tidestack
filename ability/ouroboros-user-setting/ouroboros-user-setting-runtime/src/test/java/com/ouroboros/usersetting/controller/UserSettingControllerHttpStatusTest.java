package com.ouroboros.usersetting.controller;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.vavr.control.Try;

import com.ouroboros.AppContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.usersetting.service.UserSettingProvider;
import com.ouroboros.usersetting.service.UserSettingService;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserSettingControllerHttpStatusTest {

  @Test
  void missingSettingShouldReturnHttp404() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));
    try {
      UserSettingProvider provider = mock(UserSettingProvider.class);
      when(provider.getSetting(anyString(), anyString(), anyString())).thenReturn(Optional.empty());

      UserSettingService service = mock(UserSettingService.class);
      when(service.deleteSetting(anyString(), anyString(), anyString())).thenReturn(Try.success(null));

      var controller = new UserSettingController(
          Collections.singletonList(provider),
          service,
          mock(AppContext.class)
      );
      var mockMvc = MockMvcBuilders.standaloneSetup(controller)
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      mockMvc.perform(get("/api/user-setting/get").param("settingName", "missing"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value(404))
          .andExpect(jsonPath("$.msg").value("设置项不存在"));

      mockMvc.perform(get("/api/user-setting/get-value").param("settingName", "missing"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value(404))
          .andExpect(jsonPath("$.msg").value("设置项不存在"));

      mockMvc.perform(delete("/api/user-setting").param("settingName", "missing"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value(404))
          .andExpect(jsonPath("$.msg").value("设置项不存在"));
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  @Test
  void missingSeamIdentityShouldIgnoreLegacyPrincipalAndKeep404Contract() throws Exception {
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-principal", "n/a"));
    CurrentSecurityContextHolder.clearDelegate();
    try {
      UserSettingProvider provider = mock(UserSettingProvider.class);
      when(provider.getSetting(isNull(), anyString(), anyString())).thenReturn(Optional.empty());

      UserSettingService service = mock(UserSettingService.class);
      when(service.deleteSetting(anyString(), anyString(), anyString())).thenReturn(Try.success(null));

      var controller = new UserSettingController(
          Collections.singletonList(provider),
          service,
          mock(AppContext.class)
      );
      var mockMvc = MockMvcBuilders.standaloneSetup(controller)
          .setControllerAdvice(new WebApiResultHttpStatusAdvice())
          .build();

      mockMvc.perform(get("/api/user-setting/get").param("settingName", "missing"))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value(404))
          .andExpect(jsonPath("$.msg").value("设置项不存在"));

      verify(provider).getSetting(isNull(), anyString(), eq("missing"));
    } finally {
      SecurityContextHolder.clearContext();
      CurrentSecurityContextHolder.clearDelegate();
    }
  }
}
