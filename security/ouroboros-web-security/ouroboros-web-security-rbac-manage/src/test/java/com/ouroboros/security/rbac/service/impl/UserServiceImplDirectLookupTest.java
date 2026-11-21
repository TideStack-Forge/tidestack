package com.ouroboros.security.rbac.service.impl;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.util.DataMaps;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.security.rbac.entity.UserEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceImplDirectLookupTest {

  @Test
  void failedReadsMustNotBecomeMissingUsersOrPasswords() {
    var service = new UserServiceImpl();
    TypedDataModel<String, UserEntity> model = mock(TypedDataModel.class);
    var cause = new IllegalStateException("database unavailable");
    when(model.get("admin")).thenReturn(Try.failure(cause));
    ReflectionTestUtils.setField(service, "dataModel", model);

    assertSame(cause, assertThrows(OuroborosException.class,
        () -> service.getByUserIdentity("admin")).getCause());
    assertSame(cause, assertThrows(OuroborosException.class,
        () -> service.getPasswordByUserIdentity("admin")).getCause());
  }

  @Test
  void successfulLookupOfMissingUserStillReturnsEmpty() {
    var service = new UserServiceImpl();
    TypedDataModel<String, UserEntity> model = mock(TypedDataModel.class);
    when(model.get("missing")).thenReturn(Try.success(null));
    ReflectionTestUtils.setField(service, "dataModel", model);

    assertTrue(service.getByUserIdentity("missing").isEmpty());
    assertTrue(service.getPasswordByUserIdentity("missing").isEmpty());
  }

  @Test
  void directUserLookupShouldUsePrimaryKeyAndHidePassword() {
    var service = new UserServiceImpl();
    TypedDataModel<String, UserEntity> dataModel = mock(TypedDataModel.class);
    UserEntity stored = new UserEntity();
    stored.setUserIdentity("admin");
    stored.setPassword("encoded-password");
    when(dataModel.get("admin")).thenReturn(Try.success(stored));
    ReflectionTestUtils.setField(service, "dataModel", dataModel);

    var result = service.getByUserIdentity("admin");

    assertTrue(result.isPresent());
    assertEquals("admin", result.get().getUserIdentity());
    assertNull(result.get().getPassword());
    verify(dataModel).get("admin");
  }

  @Test
  void directPasswordLookupShouldUsePrimaryKeyAndReturnStoredHash() {
    var service = new UserServiceImpl();
    TypedDataModel<String, UserEntity> dataModel = mock(TypedDataModel.class);
    UserEntity stored = new UserEntity();
    stored.setUserIdentity("admin");
    stored.setPassword("encoded-password");
    when(dataModel.get("admin")).thenReturn(Try.success(stored));
    ReflectionTestUtils.setField(service, "dataModel", dataModel);

    var result = service.getPasswordByUserIdentity("admin");

    assertTrue(result.isPresent());
    assertEquals("encoded-password", result.get());
    verify(dataModel).get("admin");
  }

  @Test
  void userEntityConversionShouldSupportPersistedAuditFields() {
    var row = new LinkedHashMap<String, Object>();
    row.put("userIdentity", "admin");
    row.put("password", "encoded-password");
    row.put("avatar", null);
    row.put("isEnabled", true);
    row.put("createdBy", null);
    row.put("createdAt", Timestamp.valueOf("2026-07-24 16:50:40"));
    row.put("updatedBy", null);
    row.put("updatedAt", LocalDateTime.of(2026, 7, 25, 9, 30, 0));
    row.put("isDeleted", false);
    row.put("deletedBy", null);
    row.put("deletedAt", null);

    var converted = DataMaps.tryToBean(UserEntity.class, row);

    assertTrue(converted.isSuccess(), () -> converted.getCause().getMessage());
    assertEquals("admin", converted.get().getUserIdentity());
    assertEquals("encoded-password", converted.get().getPassword());
  }
}
