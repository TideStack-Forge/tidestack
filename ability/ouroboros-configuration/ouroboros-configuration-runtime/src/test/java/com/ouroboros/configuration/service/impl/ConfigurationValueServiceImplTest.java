package com.ouroboros.configuration.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.configuration.ConfigurationRevision;
import com.ouroboros.configuration.model.RuntimeConfigurationValueModel;
import com.ouroboros.data.model.TypedDataModel;

import io.vavr.control.Try;

@ExtendWith(MockitoExtension.class)
class ConfigurationValueServiceImplTest {

  @Mock
  private TypedDataModel<String, RuntimeConfigurationValueModel> dataModel;

  @InjectMocks
  private ConfigurationValueServiceImpl service;

  @Captor
  private ArgumentCaptor<RuntimeConfigurationValueModel> modelCaptor;

  @Captor
  private ArgumentCaptor<Map<String, Object>> whereCaptor;

  @Captor
  private ArgumentCaptor<Map<String, Object>> updateCaptor;

  @Test
  void firstCasWriteUsesDefaultRevisionAndInsertsRevisionOne() {
    String key = "feature.enabled";
    String defaultValue = "false";
    when(dataModel.get(key)).thenReturn(Try.success(null));
    when(dataModel.insert(any(RuntimeConfigurationValueModel.class)))
        .thenReturn(Try.success(new RuntimeConfigurationValueModel()));

    RuntimeConfigurationValueModel model = model(key, "true");
    boolean updated = service.update(model,
        ConfigurationRevision.defaultRevision(key, defaultValue),
        ConfigurationRevision.defaultRevision(key, defaultValue));

    assertTrue(updated);
    verify(dataModel).insert(modelCaptor.capture());
    assertEquals(1L, modelCaptor.getValue().getRevision());
    assertEquals("true", modelCaptor.getValue().getValue());
  }

  @Test
  void firstCasWriteRejectsStaleDefaultRevisionWithoutInsert() {
    String key = "feature.enabled";
    when(dataModel.get(key)).thenReturn(Try.success(null));

    boolean updated = service.update(model(key, "true"), "default-stale", "default-current");

    assertFalse(updated);
    verify(dataModel, never()).insert(any(RuntimeConfigurationValueModel.class));
  }

  @Test
  void duplicateFirstWriteIsReportedAsCasConflictAfterConcurrentWinner() {
    String key = "feature.enabled";
    RuntimeConfigurationValueModel winner = model(key, "winner");
    winner.setRevision(1L);
    when(dataModel.get(key)).thenReturn(Try.success(null), Try.success(winner));
    when(dataModel.insert(any(RuntimeConfigurationValueModel.class)))
        .thenReturn(Try.failure(new IllegalStateException("duplicate key")));

    boolean updated = service.update(model(key, "loser"), "default-token", "default-token");

    assertFalse(updated);
    verify(dataModel).insert(any(RuntimeConfigurationValueModel.class));
  }

  @Test
  void concurrentFirstCasWritesAllowOnlyOneWinner() throws Exception {
    String key = "feature.enabled";
    String defaultRevision = ConfigurationRevision.defaultRevision(key, "false");
    AtomicReference<RuntimeConfigurationValueModel> stored = new AtomicReference<>();
    AtomicInteger initialReads = new AtomicInteger();
    CountDownLatch bothReadersReady = new CountDownLatch(2);
    when(dataModel.get(key)).thenAnswer(invocation -> {
      if (initialReads.incrementAndGet() <= 2) {
        bothReadersReady.countDown();
        if (!bothReadersReady.await(5, TimeUnit.SECONDS)) {
          throw new AssertionError("并发 CAS 测试未等到两个读取者");
        }
      }
      return Try.success(stored.get());
    });
    when(dataModel.insert(any(RuntimeConfigurationValueModel.class))).thenAnswer(invocation -> {
      RuntimeConfigurationValueModel candidate = invocation.getArgument(0, RuntimeConfigurationValueModel.class);
      if (stored.compareAndSet(null, candidate)) {
        return Try.success(candidate);
      }
      return Try.failure(new IllegalStateException("duplicate key"));
    });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      Future<Boolean> first = executor.submit(() ->
          service.update(model(key, "first"), defaultRevision, defaultRevision));
      Future<Boolean> second = executor.submit(() ->
          service.update(model(key, "second"), defaultRevision, defaultRevision));

      assertEquals(1, (first.get() ? 1 : 0) + (second.get() ? 1 : 0));
      assertTrue(stored.get() != null);
      assertEquals(1L, stored.get().getRevision());
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void existingRevisionIsComparedAndIncrementedAtomically() {
    String key = "feature.enabled";
    RuntimeConfigurationValueModel existing = model(key, "old");
    existing.setRevision(2L);
    when(dataModel.get(key)).thenReturn(Try.success(existing));
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));

    boolean updated = service.update(model(key, "new"), "2", "unused");

    assertTrue(updated);
    verify(dataModel).update(whereCaptor.capture(), updateCaptor.capture());
    assertEquals(key, whereCaptor.getValue().get("key"));
    assertEquals(2L, whereCaptor.getValue().get("revision"));
    assertEquals("new", updateCaptor.getValue().get("value"));
    assertEquals(3L, updateCaptor.getValue().get("revision"));
  }

  @Test
  void staleExistingRevisionDoesNotWrite() {
    String key = "feature.enabled";
    RuntimeConfigurationValueModel existing = model(key, "old");
    existing.setRevision(2L);
    when(dataModel.get(key)).thenReturn(Try.success(existing));

    boolean updated = service.update(model(key, "new"), "1", "unused");

    assertFalse(updated);
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void legacyNullRevisionUsesStoredTokenAndNullSafeCasPredicate() {
    String key = "feature.enabled";
    RuntimeConfigurationValueModel existing = model(key, "legacy");
    when(dataModel.get(key)).thenReturn(Try.success(existing));
    when(dataModel.update(anyMap(), anyMap())).thenReturn(Try.success(1L));

    boolean updated = service.update(model(key, "new"),
        ConfigurationRevision.legacyStoredRevision(key, "legacy"),
        "unused");

    assertTrue(updated);
    verify(dataModel).update(whereCaptor.capture(), updateCaptor.capture());
    Map<String, Object> where = whereCaptor.getValue();
    assertTrue(where.containsKey("revision"));
    assertNull(where.get("revision"));
    assertEquals("legacy", where.get("value"));
    assertEquals(1L, updateCaptor.getValue().get("revision"));
  }

  @Test
  void legacyTokenChangesWhenStoredValueChanges() {
    String key = "feature.enabled";
    RuntimeConfigurationValueModel existing = model(key, "changed");
    when(dataModel.get(key)).thenReturn(Try.success(existing));

    boolean updated = service.update(model(key, "new"),
        ConfigurationRevision.legacyStoredRevision(key, "legacy"),
        "unused");

    assertFalse(updated);
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void removalUsesObservedIncarnationAndVersionAndRejectsStalePages() {
    var existing = model("host", "override.invalid");
    existing.setRevision(3L);
    existing.setGeneration("original");
    when(dataModel.get("host")).thenReturn(Try.success(existing));
    when(dataModel.delete(anyMap())).thenReturn(Try.success(1L));
    assertTrue(service.removeOverride("host", "original:3", "default-token"));
    verify(dataModel).delete(whereCaptor.capture());
    assertEquals(Map.of("key", "host", "revision", 3L, "generation", "original"), whereCaptor.getValue());
    assertFalse(service.removeOverride("host", "original:2", "default-token"));
  }

  @Test
  void recreatedRowsCannotReuseAnOldTokenEvenWhenTheirVersionAndValueMatch() {
    var stored = new AtomicReference<RuntimeConfigurationValueModel>();
    when(dataModel.get("host")).thenAnswer(invocation -> Try.success(stored.get()));
    when(dataModel.insert(any(RuntimeConfigurationValueModel.class))).thenAnswer(invocation -> {
      RuntimeConfigurationValueModel row = invocation.getArgument(0);
      stored.set(row);
      return Try.success(row);
    });
    assertTrue(service.update(model("host", "same"), "default", "default"));
    var original = stored.get();
    String oldToken = ConfigurationRevision.storedRevision("host", "same", original.getRevision(), original.getGeneration());
    stored.set(null);
    assertTrue(service.update(model("host", "same"), "default", "default"));
    assertEquals(original.getRevision(), stored.get().getRevision());
    assertFalse(original.getGeneration().equals(stored.get().getGeneration()));
    assertFalse(service.update(model("host", "stale write"), oldToken, "default"));
    assertFalse(service.removeOverride("host", oldToken, "default"));
    verify(dataModel, never()).delete(anyMap());
    verify(dataModel, never()).update(anyMap(), anyMap());
  }

  @Test
  void absentRemovalRequiresCurrentDefaultTokenAndDatabaseErrorsPropagate() {
    when(dataModel.get("host")).thenReturn(Try.success(null));
    assertTrue(service.removeOverride("host", "default", "default"));
    assertFalse(service.removeOverride("host", "stale", "default"));
    var row = model("host", "stored");
    row.setRevision(3L);
    when(dataModel.get("host")).thenReturn(Try.success(row));
    when(dataModel.delete(anyMap())).thenReturn(Try.failure(new IllegalStateException("storage unavailable")));
    org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
        () -> service.removeOverride("host", "3", "default"));
  }

  private RuntimeConfigurationValueModel model(String key, String value) {
    RuntimeConfigurationValueModel model = new RuntimeConfigurationValueModel();
    model.setKey(key);
    model.setValue(value);
    model.setUpdateBy("test");
    return model;
  }
}
