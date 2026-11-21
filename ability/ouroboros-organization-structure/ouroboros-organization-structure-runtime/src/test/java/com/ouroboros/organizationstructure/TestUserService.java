package com.ouroboros.organizationstructure;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.vavr.control.Try;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.impl.UserServiceImpl;

class TestUserService {

  private TestableUserService service;

  @BeforeEach
  void setUp() {
    service = new TestableUserService();
    ReflectionTestUtils.setField(service, "self", service);
  }

  @Test
  void searchAll_shouldReturnCopiesWithoutPasswords() {
    UserEntity stored = user("123", "encoded-password");
    service.setUsers(Collections.singletonList(stored));

    List<UserEntity> result = service.searchAll();

    assertEquals(1, result.size());
    assertEquals("123", result.get(0).getUserIdentity());
    assertNull(result.get(0).getPassword());
    assertEquals("encoded-password", stored.getPassword());
    assertNotSame(stored, result.get(0));
  }

  @Test
  void getByUserIdentity_shouldUseDirectLookupAndMaskPassword() {
    UserEntity stored = user("123", "encoded-123");
    TypedDataModel<String, UserEntity> dataModel = mock(TypedDataModel.class);
    when(dataModel.get("123")).thenReturn(Try.success(stored));
    ReflectionTestUtils.setField(service, "dataModel", dataModel);

    Optional<UserEntity> result = service.getByUserIdentity("123");

    assertTrue(result.isPresent());
    assertEquals("123", result.get().getUserIdentity());
    assertNull(result.get().getPassword());
    assertEquals("encoded-123", stored.getPassword());
    verify(dataModel).get("123");
  }

  @Test
  void getPasswordByUserIdentity_shouldReadOriginalPassword() {
    UserEntity stored = user("123", "encoded-123");
    TypedDataModel<String, UserEntity> dataModel = mock(TypedDataModel.class);
    when(dataModel.get("123")).thenReturn(Try.success(stored));
    ReflectionTestUtils.setField(service, "dataModel", dataModel);

    Optional<String> result = service.getPasswordByUserIdentity("123");

    assertEquals(Optional.of("encoded-123"), result);
    verify(dataModel).get("123");
  }

  private UserEntity user(String userIdentity, String password) {
    UserEntity entity = new UserEntity();
    entity.setUserIdentity(userIdentity);
    entity.setPassword(password);
    entity.setAvatar("avatar");
    return entity;
  }

  private static class TestableUserService extends UserServiceImpl {
    private List<UserEntity> users = Collections.emptyList();

    private void setUsers(List<UserEntity> users) {
      this.users = users;
    }

    @Override
    public List<UserEntity> doSearchAll() {
      return users;
    }
  }
}
