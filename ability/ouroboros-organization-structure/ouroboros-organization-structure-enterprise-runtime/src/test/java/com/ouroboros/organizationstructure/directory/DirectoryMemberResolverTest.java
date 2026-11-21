package com.ouroboros.organizationstructure.directory;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;

class DirectoryMemberResolverTest {
  private final MemoryDirectoryStore store = new MemoryDirectoryStore();
  private final UserSubjectCenter subjects = mock(UserSubjectCenter.class);
  private final DirectoryMemberResolver resolver = new DirectoryMemberResolver(store, subjects);

  @Test void employeeAddressWorksWithoutSsoButPrincipalResolutionRequiresExplicitUserRelation() {
    var binding = binding();
    assertThat(resolver.forEmployee("connection", "employee")).contains(binding);
    when(subjects.getAll()).thenReturn(List.of());
    assertThat(resolver.toPlatform("connection", "member")).isEmpty();
    var relation = relation("user"); when(subjects.getAll()).thenReturn(List.of(relation));
    when(subjects.getAllByUser("user")).thenReturn(List.of(relation));
    assertThat(resolver.toPlatform("connection", "member")).contains(new PlatformPrincipalRef("user"));
    assertThat(resolver.toExternalMember("connection", new PlatformPrincipalRef("user"))).contains("member");
  }

  @Test void inactiveAndAmbiguousRelationsFailWithoutFallback() {
    var binding = binding(); binding.setActive(false);
    assertThatThrownBy(() -> resolver.forEmployee("connection", "employee")).hasMessageContaining("已失效");
    assertThatThrownBy(() -> resolver.toPlatform("connection", "member")).hasMessageContaining("已失效");
    binding.setActive(true); var first = relation("u1"); var second = relation("u2");
    when(subjects.getAll()).thenReturn(List.of(first, second));
    assertThatThrownBy(() -> resolver.toPlatform("connection", "member")).hasMessageContaining("多个平台账号");
  }

  private DirectoryBinding binding() {
    var b = new DirectoryBinding(); b.setConnectionId("connection"); b.setObjectType("MEMBER"); b.setExternalId("member");
    b.setLocalId("employee"); b.setActive(true); DirectoryBindingCommands.route(b); return store.insert(b);
  }
  private UserSubject relation(String user) {
    var relation = mock(UserSubject.class); when(relation.getUserIdentity()).thenReturn(user);
    when(relation.getSubjectId()).thenReturn("employee"); when(relation.getSubjectType()).thenReturn("employee"); return relation;
  }
}
