package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.dev.runtime.model.DebugKeyMaterial;

class DatabaseDebugKeyMaterialProviderTest {
  @Test
  void shouldReadDebugKeyMaterialFromDatabase() {
    DevKeyStore store = mock(DevKeyStore.class);
    when(store.load("crm", "alice.dev@example.com")).thenReturn(appMaterial("crm", "alice.dev@example.com", "debug-key-001"));
    DatabaseDebugKeyMaterialProvider provider = new DatabaseDebugKeyMaterialProvider(store, new AuthenticationPrincipalTestAppProfileResolver());

    Try<DebugKeyMaterial> result = provider.read("crm", "alice.dev@example.com");

    assertTrue(result.isSuccess());
    assertEquals("alice_dev_example_com", result.get().getProfileId());
    assertEquals("debug-key-001", result.get().getDebugKey());
    assertEquals("SESSION_TUNNEL", result.get().getRuntimeStrategy().name());
  }

  @Test
  void shouldRejectMissingDebugKeyMaterialPayload() {
    DevKeyStore store = mock(DevKeyStore.class);
    when(store.load("crm", "alice.dev@example.com")).thenReturn(null);
    DatabaseDebugKeyMaterialProvider provider = new DatabaseDebugKeyMaterialProvider(store, new AuthenticationPrincipalTestAppProfileResolver());

    Try<DebugKeyMaterial> result = provider.read("crm", "alice.dev@example.com");

    assertTrue(result.isFailure());
    assertTrue(String.valueOf(result.getCause().getMessage()).contains("未找到开发密钥材料"));
  }

  @Test
  void shouldFindDebugKeyMaterialByAppAndDevKey() {
    DevKeyStore store = mock(DevKeyStore.class);
    when(store.findByDevKey("crm", "dev-key-bob")).thenReturn(appMaterial("crm", "bob.dev@example.com", "dev-key-bob"));
    when(store.findByDevKey("crm", "dev-key-erp")).thenReturn(null);
    DatabaseDebugKeyMaterialProvider provider = new DatabaseDebugKeyMaterialProvider(store, new AuthenticationPrincipalTestAppProfileResolver());

    Try<DebugKeyMaterial> result = provider.findByDevKey("crm", "dev-key-bob");

    assertTrue(result.isSuccess());
    assertEquals("bob_dev_example_com", result.get().getProfileId());

    Try<DebugKeyMaterial> otherAppResult = provider.findByDevKey("crm", "dev-key-erp");
    assertTrue(otherAppResult.isFailure());
  }

  private static com.ouroboros.dev.app.model.DebugKeyMaterial appMaterial(String appName, String userIdentity, String devKey) {
    return com.ouroboros.dev.app.model.DebugKeyMaterial.builder()
        .appName(appName)
        .userIdentity(userIdentity)
        .debugKey(devKey)
        .runtimeStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .expiresAt(Instant.parse("2026-03-24T09:30:00Z"))
        .build();
  }
}
