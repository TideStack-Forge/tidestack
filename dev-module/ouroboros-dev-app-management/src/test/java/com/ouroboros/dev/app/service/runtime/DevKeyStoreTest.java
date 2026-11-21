package com.ouroboros.dev.app.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.app.model.DevKeyEntity;
import com.ouroboros.dev.runtime.model.DebugAccessStrategy;
import com.ouroboros.util.Json;

@ExtendWith(MockitoExtension.class)
class DevKeyStoreTest {
  @Mock
  private TypedDataModel<String, DevKeyEntity> dataModel;

  private DevKeyStore store;

  @BeforeEach
  void setUp() {
    store = new DevKeyStore(dataModel);
  }

  @Test
  void shouldSaveDevKeyMaterialByReplacingAppUserRow() {
    when(dataModel.delete(appUserWhere("crm", "alice@example.com"))).thenReturn(Try.success(1L));
    when(dataModel.insert(any())).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    store.save("crm", "alice@example.com", material("crm", "alice@example.com", "alice_example_com").build());

    verify(dataModel).delete(appUserWhere("crm", "alice@example.com"));
    ArgumentCaptor<DevKeyEntity> captor = ArgumentCaptor.forClass(DevKeyEntity.class);
    verify(dataModel).insert(captor.capture());
    DevKeyEntity entity = captor.getValue();
    assertEquals("crm", entity.getAppName());
    assertEquals("alice@example.com", entity.getUserIdentity());
    assertEquals("debug-key-001", entity.getDevKey());
    assertEquals("SESSION_TUNNEL", entity.getRuntimeStrategy());
    assertEquals(LocalDateTime.ofInstant(Instant.parse("2026-04-12T01:02:03Z"), ZoneOffset.UTC), entity.getCreatedAt());
  }

  @Test
  void shouldLoadDevKeyMaterialFromDatabase() {
    when(dataModel.query(queryStatement(appUserWhere("crm", "alice@example.com"))))
        .thenReturn(Try.success(Collections.singletonList(entity("crm", "alice@example.com", "debug-key-001"))));

    DebugKeyMaterial loaded = store.load("crm", "alice@example.com");

    assertEquals("crm", loaded.getAppName());
    assertEquals("alice@example.com", loaded.getUserIdentity());
    assertEquals("debug-key-001", loaded.getDebugKey());
    assertEquals(DebugAccessStrategy.SESSION_TUNNEL, loaded.getRuntimeStrategy());
    assertEquals(Instant.parse("2026-04-12T01:02:03Z"), loaded.getCreatedAt());
  }

  @Test
  void shouldReturnNullWhenDevKeyMaterialDoesNotExist() {
    when(dataModel.query(queryStatement(appUserWhere("crm", "alice@example.com"))))
        .thenReturn(Try.success(Collections.emptyList()), Try.success(Collections.emptyList()));

    assertNull(store.load("crm", "alice@example.com"));
    assertFalse(store.exists("crm", "alice@example.com"));
  }

  @Test
  void shouldFindDevKeyMaterialByAppAndDevKey() {
    when(dataModel.query(queryStatement(appDevKeyWhere("crm", "dev-key-bob"))))
        .thenReturn(Try.success(Collections.singletonList(entity("crm", "bob@example.com", "dev-key-bob"))));

    DebugKeyMaterial loaded = store.findByDevKey("crm", "dev-key-bob");

    assertEquals("crm", loaded.getAppName());
    assertEquals("bob@example.com", loaded.getUserIdentity());
    assertEquals("dev-key-bob", loaded.getDebugKey());
  }

  @Test
  void shouldRoundTripDebugKeyMaterialJsonSerialization() {
    DebugKeyMaterial material = material("crm", "alice@example.com", "alice_example_com").build();

    String json = Json.toPrettyJsonString(material);
    DebugKeyMaterial restored = Json.toBean(DebugKeyMaterial.class, json);

    assertEquals(material.getAppName(), restored.getAppName());
    assertEquals(material.getUserIdentity(), restored.getUserIdentity());
    assertEquals(material.getProfileId(), restored.getProfileId());
    assertEquals(material.getDebugKey(), restored.getDebugKey());
    assertEquals(material.getRuntimeStrategy(), restored.getRuntimeStrategy());
    assertEquals(material.getCreatedAt(), restored.getCreatedAt());
    assertEquals(material.getExpiresAt(), restored.getExpiresAt());
    assertEquals(material.getDescription(), restored.getDescription());
    assertEquals(material.getLastRotatedAt(), restored.getLastRotatedAt());
    assertEquals(material.getCreatedBy(), restored.getCreatedBy());
    assertEquals(material.getUpdatedBy(), restored.getUpdatedBy());
  }

  private static DebugKeyMaterial.Builder material(String appName, String userIdentity, String profileId) {
    return DebugKeyMaterial.builder()
        .appName(appName)
        .userIdentity(userIdentity)
        .profileId(profileId)
        .debugKey("debug-key-001")
        .runtimeStrategy(DebugAccessStrategy.SESSION_TUNNEL)
        .createdAt(Instant.parse("2026-04-12T01:02:03Z"))
        .expiresAt(Instant.parse("2026-04-13T01:02:03Z"))
        .description("debug key for integration testing")
        .lastRotatedAt(Instant.parse("2026-04-12T03:04:05Z"))
        .createdBy("alice")
        .updatedBy("bob");
  }

  private static DevKeyEntity entity(String appName, String userIdentity, String devKey) {
    DevKeyEntity entity = new DevKeyEntity();
    entity.setAppName(appName);
    entity.setUserIdentity(userIdentity);
    entity.setDevKey(devKey);
    entity.setRuntimeStrategy(DebugAccessStrategy.SESSION_TUNNEL.name());
    entity.setCreatedAt(LocalDateTime.ofInstant(Instant.parse("2026-04-12T01:02:03Z"), ZoneOffset.UTC));
    entity.setExpiresAt(LocalDateTime.ofInstant(Instant.parse("2026-04-13T01:02:03Z"), ZoneOffset.UTC));
    entity.setDescription("debug key for integration testing");
    entity.setLastRotatedAt(LocalDateTime.ofInstant(Instant.parse("2026-04-12T03:04:05Z"), ZoneOffset.UTC));
    entity.setCreatedBy("alice");
    entity.setUpdatedBy("bob");
    return entity;
  }

  private static Map<String, Object> queryStatement(Map<String, Object> where) {
    return Collections.singletonMap(Keyword.WHERE.toString(), where);
  }

  private static Map<String, Object> appUserWhere(String appName, String userIdentity) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    where.put("userIdentity", userIdentity);
    return where;
  }

  private static Map<String, Object> appDevKeyWhere(String appName, String devKey) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    where.put("devKey", devKey);
    return where;
  }
}
