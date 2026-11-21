package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugKeyMaterial;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.exception.BusinessException;

class DefaultDebugProfileAuthorizationServiceTest {

  @Test
  void shouldRejectMissingApp() {
    DefaultDebugProfileAuthorizationService service = new DefaultDebugProfileAuthorizationService(
        provider(debugKeyMaterial("alice", "dev-key-001")),
        appManagementService(false)
    );

    Try<?> result = service.authorize("crm", "dev-key-001");

    assertTrue(result.isFailure());
    assertTrue(result.getCause() instanceof BusinessException);
  }

  @Test
  void shouldRejectDevKeyMismatch() {
    DefaultDebugProfileAuthorizationService service = new DefaultDebugProfileAuthorizationService(
        providerFailure(new BusinessException(404, "未找到开发密钥材料")),
        appManagementService(true)
    );

    Try<?> result = service.authorize("crm", "wrong-key");

    assertTrue(result.isFailure());
  }

  @Test
  void shouldRejectMissingProfileMaterial() {
    DefaultDebugProfileAuthorizationService service = new DefaultDebugProfileAuthorizationService(
        provider(debugKeyMaterial(" ", "dev-key-001")),
        appManagementService(true)
    );

    Try<?> result = service.authorize("crm", "dev-key-001");

    assertTrue(result.isFailure());
  }

  @Test
  void shouldRejectExpiredDevKeyMaterial() {
    DefaultDebugProfileAuthorizationService service = new DefaultDebugProfileAuthorizationService(
        provider(expiredDebugKeyMaterial("alice", "dev-key-001")),
        appManagementService(true)
    );

    Try<?> result = service.authorize("crm", "dev-key-001");

    assertTrue(result.isFailure());
  }

  @Test
  void shouldReturnCapabilitySummaryWhenAppAndDevKeyMatch() {
    DefaultDebugProfileAuthorizationService service = new DefaultDebugProfileAuthorizationService(
        provider(debugKeyMaterial("alice", "dev-key-001")),
        appManagementService(true)
    );

    Try<com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO> result = service.authorize("crm", "dev-key-001");

    assertTrue(result.isSuccess());
    assertEquals("alice", result.get().getProfileId());
    assertEquals(Boolean.TRUE, result.get().getAuthorized());
    assertEquals("SESSION_TUNNEL", result.get().getRuntimeStrategy());
    assertTrue(result.get().getExpiresAt().isAfter(Instant.now()));
  }

  private static DebugKeyMaterialProvider provider(DebugKeyMaterial material) {
    return new DebugKeyMaterialProvider() {
      @Override
      public Try<DebugKeyMaterial> read(String appName, String profileId) {
        return Try.success(material);
      }

      @Override
      public Try<DebugKeyMaterial> findByDevKey(String appName, String devKey) {
        return Try.success(material);
      }
    };
  }

  private static DebugKeyMaterialProvider providerFailure(BusinessException exception) {
    return new DebugKeyMaterialProvider() {
      @Override
      public Try<DebugKeyMaterial> read(String appName, String profileId) {
        return Try.failure(exception);
      }

      @Override
      public Try<DebugKeyMaterial> findByDevKey(String appName, String devKey) {
        return Try.failure(exception);
      }
    };
  }

  private static AppManagementService appManagementService(boolean appExists) {
    AppManagementService appManagementService = mock(AppManagementService.class);
    when(appManagementService.appExists("crm")).thenReturn(appExists);
    return appManagementService;
  }

  private static DebugKeyMaterial debugKeyMaterial(String profileId, String debugKey) {
    return DebugKeyMaterial.builder()
        .profileId(profileId)
        .debugKey(debugKey)
        .runtimeStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .runtimeCapability(RuntimeCapability.builder()
            .supportsDebugSession(true)
            .supportsDebugPort(true)
            .build())
        .expiresAt(Instant.now().plusSeconds(3600))
        .build();
  }

  private static DebugKeyMaterial expiredDebugKeyMaterial(String profileId, String debugKey) {
    return DebugKeyMaterial.builder()
        .profileId(profileId)
        .debugKey(debugKey)
        .runtimeStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .runtimeCapability(RuntimeCapability.builder()
            .supportsDebugSession(true)
            .supportsDebugPort(true)
            .build())
        .expiresAt(Instant.parse("2000-01-01T00:00:00Z"))
        .build();
  }
}
