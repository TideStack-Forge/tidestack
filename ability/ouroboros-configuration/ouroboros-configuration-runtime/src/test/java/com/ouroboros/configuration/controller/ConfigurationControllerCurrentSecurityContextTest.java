package com.ouroboros.configuration.controller;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.service.RuntimeConfigurationService;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ConfigurationControllerCurrentSecurityContextTest {

  @AfterEach
  void clearSecurityContextHolder() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void updateUsesCurrentSecurityContextUserIdentityAsOperator() {
    RuntimeConfigurationService service = mock(RuntimeConfigurationService.class);
    ConfigurationController controller = new ConfigurationController();
    controller.service = service;

    CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(new DefaultSecurityIdentity(() -> "seam-operator")));

    ConfigurationDTO dto = configuration("feature.enabled", true, "default-token");
    WebApiResult<Map<String, com.ouroboros.configuration.model.ConfigurationModel>> result =
        controller.update(List.of(dto));

    verify(service).updateManaged(anyMap(), eq("seam-operator"), eq(Map.of("feature.enabled", "default-token")));
    org.junit.jupiter.api.Assertions.assertNotNull(result);
  }

  @Test
  void updateRejectsBatchWhenAnyValueIsMissingExpectedRevision() {
    RuntimeConfigurationService service = mock(RuntimeConfigurationService.class);
    ConfigurationController controller = new ConfigurationController();
    controller.service = service;

    ConfigurationDTO guarded = configuration("feature.enabled", true, "default-token");
    ConfigurationDTO unguarded = configuration("feature.limit", 10, null);

    assertThrows(PlatformException.class, () -> controller.update(List.of(guarded, unguarded)));
    verify(service, never()).updateManaged(anyMap(), org.mockito.ArgumentMatchers.anyString(), anyMap());
  }

  private ConfigurationDTO configuration(String key, Object value, String expectedRevision) {
    ConfigurationDTO dto = new ConfigurationDTO();
    dto.setKey(key);
    dto.setValue(value);
    dto.setExpectedRevision(expectedRevision);
    return dto;
  }
}
