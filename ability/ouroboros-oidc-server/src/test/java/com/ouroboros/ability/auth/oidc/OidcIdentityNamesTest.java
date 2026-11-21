package com.ouroboros.ability.auth.oidc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.rbac.PersonSubject;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.claims.provider.SubjectClaimsProvider;
import com.ouroboros.security.rbac.claims.provider.UserEntityClaimsProvider;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.util.SPI;

class OidcIdentityNamesTest {
  @Test
  void nicknameAndRegisteredNameStayDistinctAcrossEmployeeBindingChanges() {
    var account = new UserEntity();
    account.setUserIdentity("alice");
    account.setPublicSubjectId("65eb15bb-becd-4f68-a69e-068f951c4507");
    account.setNickname("小张");
    var users = mock(UserService.class);
    when(users.getByUserIdentity("alice")).thenReturn(Optional.of(account));
    var subjects = mock(SubjectCenter.class);
    var links = mock(UserSubjectCenter.class);
    var link = mock(UserSubject.class);
    when(link.getSubjectType()).thenReturn("employee");
    when(link.getSubjectId()).thenReturn("employee-1");
    when(links.getByUser("alice")).thenReturn(Optional.of(link));
    var employee = employee("张三");
    when(subjects.getByTypeAndId("employee", "employee-1")).thenReturn(Optional.of(employee));

    try (var subjectDefault = mockStatic(SubjectCenter.class);
         var linkDefault = mockStatic(UserSubjectCenter.class);
         var spi = mockStatic(SPI.class)) {
      subjectDefault.when(SubjectCenter::instance).thenReturn(subjects);
      linkDefault.when(UserSubjectCenter::instance).thenReturn(links);
      spi.when(() -> SPI.getServiceStream(ClaimsProvider.class)).thenAnswer(call -> Stream.empty());
      var aggregator = new ClaimsAggregator(List.of(new SubjectClaimsProvider(), new UserEntityClaimsProvider(users)));

      var bound = new RbacUserModel(account, aggregator, subjects, links);
      var identity = OidcIdentityClaims.project(bound, Set.of("openid", "profile"));
      assertEquals("张三", bound.getFormalName());
      assertEquals("小张", bound.getNickname());
      assertEquals("小张", bound.getDisplayName());
      assertEquals("张三", identity.get("name"));
      assertEquals("小张", identity.get("nickname"));
      assertFalse(identity.containsKey("displayName"));
      assertEquals(Set.of("sub"), OidcIdentityClaims.project(bound, Set.of("openid")).keySet());

      when(links.getByUser("alice")).thenReturn(Optional.empty());
      var unbound = new RbacUserModel(account, aggregator, subjects, links);
      var unboundIdentity = OidcIdentityClaims.project(unbound, Set.of("openid", "profile"));
      assertNull(unbound.getFormalName());
      assertEquals("小张", unbound.getDisplayName());
      assertFalse(unboundIdentity.containsKey("name"));
      assertEquals("小张", unboundIdentity.get("nickname"));
      assertEquals(identity.get("sub"), unboundIdentity.get("sub"));

      when(links.getByUser("alice")).thenReturn(Optional.of(link));
      when(subjects.getByTypeAndId("employee", "employee-1")).thenReturn(Optional.of(employee("李四")));
      var rebound = new RbacUserModel(account, aggregator, subjects, links);
      assertEquals("李四", rebound.getFormalName());
      assertEquals("小张", rebound.getDisplayName());
      assertEquals("李四", OidcIdentityClaims.project(rebound, Set.of("profile")).get("name"));

      account.setNickname(null);
      var unnamed = new RbacUserModel(account, aggregator, subjects, links);
      assertEquals("李四", unnamed.getDisplayName());
      assertFalse(OidcIdentityClaims.project(unnamed, Set.of("profile")).containsKey("nickname"));
      when(links.getByUser("alice")).thenReturn(Optional.empty());
      var loginOnly = new RbacUserModel(account, aggregator, subjects, links);
      assertEquals("alice", loginOnly.getDisplayName());
      assertFalse(OidcIdentityClaims.project(loginOnly, Set.of("profile")).containsKey("name"));
      verify(users, never()).saveProfile(anyString(), anyString(), anyLong(), anyMap());
    }
  }

  private PersonSubject employee(String name) {
    return new PersonSubject() {
      public String getId() { return "employee-1"; }
      public String getName() { return name; }
      public String getType() { return "employee"; }
    };
  }
}
