package com.ouroboros.security.rbac.provider;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.DevAppHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.RoleProvider;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.security.rbac.store.BuiltinRoleDefinitionStore;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

class BuiltinRoleProviderTest {

  @AfterEach
  void clearSpi() throws Exception {
    DevAppHolder.removeAppName();
    clearSpiPrimary(RoleCenter.class);
    clearSpiPrimary(RoleProvider.class);
  }

  @Test
  void builtinRoleProviderShouldExposeAppFileBackedRolesToRoleCenter() throws Exception {
    BuiltinRoleModel builtinRole = builtinRole();
    BuiltinRoleDefinitionStore store = mock(BuiltinRoleDefinitionStore.class);
    when(store.loadAll()).thenReturn(Collections.singletonList(builtinRole));
    BuiltinRoleProvider provider = new BuiltinRoleProvider(store);
    new com.ouroboros.security.rbac.RoleCenterImpl(Collections.singletonList(provider));
    setSpiPrimary(RoleCenter.class, new com.ouroboros.security.rbac.RoleCenterImpl.Proxy());

    List<Role> roles = RoleCenter.instance().getAllRoles();
    assertTrue(roles.stream().anyMatch(role -> role.isBuiltinRole() && "builtin-reviewer".equals(role.getId())));
  }

  @Test
  void builtinRoleProviderShouldExposeSingleBuiltinRoleLookup() {
    BuiltinRoleModel builtinRole = builtinRole();
    BuiltinRoleDefinitionStore store = mock(BuiltinRoleDefinitionStore.class);
    when(store.findById("builtin-reviewer")).thenReturn(Optional.of(builtinRole));
    BuiltinRoleProvider provider = new BuiltinRoleProvider(store);

    assertTrue(provider.getRole("builtin-reviewer").filter(Role::isBuiltinRole).isPresent());
  }

  private BuiltinRoleModel builtinRole() {
    BuiltinRoleModel model = new BuiltinRoleModel();
    model.setId("builtin-reviewer");
    model.setName("内置审阅者");
    model.setAuthorities(Collections.emptyList());
    return model;
  }

  private static org.mockito.MockedStatic<com.ouroboros.util.SPI> spiBinding;

  private static <T> void setSpiPrimary(Class<T> serviceClass, T service) {
    if (spiBinding == null) spiBinding = org.mockito.Mockito.mockStatic(com.ouroboros.util.SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> com.ouroboros.util.SPI.getCachedServiceStream(serviceClass))
        .thenAnswer(ignored -> java.util.stream.Stream.of(service));
  }

  private static void clearSpiPrimary(Class<?> serviceClass) {
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    com.ouroboros.util.SPI.clearCache();
  }
}
