package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.vavr.control.Try;

import com.ouroboros.data.dsl.Keyword;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dev.app.model.AppOwnerEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppOwnerServiceImplTest {

  @Mock
  private TypedDataModel<String, AppOwnerEntity> dataModel;

  private AppOwnerServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new AppOwnerServiceImpl() {
      @Override
      protected TypedDataModel<String, AppOwnerEntity> getDataModel() {
        return dataModel;
      }
    };
  }

  @Test
  void getByAppNameReturnsOptionalOwnerAndMatchesIdentity() {
    AppOwnerEntity owner = owner("sales-app", "alice", "alice");
    when(dataModel.query(queryStatement("sales-app")))
        .thenReturn(Try.success(Collections.singletonList(owner)));

    assertEquals(Optional.of(owner), service.getByAppName("sales-app"));
    assertTrue(service.isOwner("sales-app", "alice"));
    assertFalse(service.isOwner("sales-app", "bob"));
  }

  @Test
  void assignOwnerOverwritesExistingOwnerWithFreshMetadata() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(1L));
    when(dataModel.insert(any())).thenAnswer(invocation -> Try.success(invocation.getArgument(0)));

    service.assignOwner("sales-app", "alice", "creator");

    verify(dataModel).delete(where("sales-app"));
    var captor = org.mockito.ArgumentCaptor.forClass(AppOwnerEntity.class);
    verify(dataModel).insert(captor.capture());
    AppOwnerEntity owner = captor.getValue();
    assertEquals("sales-app", owner.getAppName());
    assertEquals("alice", owner.getOwnerUserIdentity());
    assertEquals("creator", owner.getCreatedBy());
    assertNotNull(owner.getCreatedAt());
  }

  @Test
  void renameOwnerUpdatesAppName() {
    when(dataModel.update(where("sales-app"), appNameUpdate("sales-app-v2")))
        .thenReturn(Try.success(1L));

    service.renameOwner("sales-app", "sales-app-v2");

    verify(dataModel).update(where("sales-app"), appNameUpdate("sales-app-v2"));
  }

  @Test
  void deleteOwnerRemovesExistingRow() {
    when(dataModel.delete(where("sales-app"))).thenReturn(Try.success(1L));

    service.deleteOwner("sales-app");

    verify(dataModel).delete(where("sales-app"));
  }

  private static AppOwnerEntity owner(String appName, String ownerUserIdentity, String createdBy) {
    AppOwnerEntity owner = new AppOwnerEntity();
    owner.setAppName(appName);
    owner.setOwnerUserIdentity(ownerUserIdentity);
    owner.setCreatedBy(createdBy);
    owner.setCreatedAt(java.time.LocalDateTime.now());
    return owner;
  }

  private static Map<String, Object> queryStatement(String appName) {
    return Collections.<String, Object>singletonMap(Keyword.WHERE.toString(), where(appName));
  }

  private static Map<String, Object> where(String appName) {
    Map<String, Object> where = new HashMap<>();
    where.put("appName", appName);
    return where;
  }

  private static Map<String, Object> appNameUpdate(String newAppName) {
    Map<String, Object> update = new HashMap<>();
    update.put("appName", newAppName);
    return update;
  }
}
