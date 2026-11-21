package com.ouroboros.security.rbac.profile;

import com.ouroboros.security.rbac.profile.service.UserProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.profile.*;
import com.ouroboros.security.profile.UserProfilePolicy.Audience;
import com.ouroboros.security.rbac.*;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.service.UserService;

class UserProfileServiceTest {
  UserEntity account;
  UserService users;
  UserCenter center;
  UserProfilePolicySource policies;
  UserProfileService service;
  SubjectCenter subjects;
  UserSubjectCenter links;

  @BeforeEach
  void setUp() {
    account = new UserEntity(); account.setUserIdentity("alice");
    account.setPublicSubjectId("65eb15bb-becd-4f68-a69e-068f951c4507"); account.setProfileRevision(0L);
    account.setProfileValues(Map.of("historical", "preserve-me"));
    users = mock(UserService.class); center = mock(UserCenter.class); policies = mock(UserProfilePolicySource.class);
    subjects = mock(SubjectCenter.class); links = mock(UserSubjectCenter.class);
    when(center.getUserCandidates("alice")).thenReturn(List.of(new RbacUserModel(account,
        mock(com.ouroboros.security.claims.ClaimsAggregator.class), subjects, links)));
    when(users.getByUserIdentity("alice")).thenReturn(Optional.of(account));
    when(policies.current()).thenReturn(new UserProfilePolicySource.Snapshot("rules", UserProfilePolicy.defaults()));
    service = new UserProfileService(users, center, policies);
    doAnswer(call -> {
      assertSame(SystemSecurityIdentities.system(), CurrentSecurityContextHolder.getRequiredIdentity());
      assertEquals(account.getProfileRevision(), call.getArgument(2));
      Map<?, ?> patch = call.getArgument(3);
      if (patch.containsKey("nickname")) { account.setNickname((String) patch.get("nickname")); }
      account.setProfileRevision(account.getProfileRevision() + 1); return null;
    }).when(users).saveProfile(anyString(), anyString(), anyLong(), anyMap());
  }

  @Test
  void changesOnlyExplicitEditableValuesAndDoesNotExposeHiddenExtensions() {
    service.patch("alice", Audience.SELF, new UserProfileService.Patch("rules", 0L, Map.of("nickname", "  新名称  ")));
    var view = service.view("alice", Audience.SELF);
    assertEquals("新名称", ((Map<?, ?>) view.get("values")).get("nickname"));
    assertEquals(1L, view.get("profileRevision"));
    assertFalse(((Map<?, ?>) view.get("values")).containsKey("historical"));
    assertEquals("preserve-me", account.getProfileValues().get("historical"));
    assertEquals("新名称", ((Map<?, ?>) view.get("values")).get("displayName"));
  }

  @Test
  void registeredAndDisplayNamesAreReadOnlyProjectionsOfTheExistingSubject() {
    var binding = mock(UserSubject.class);
    when(binding.getSubjectType()).thenReturn("employee"); when(binding.getSubjectId()).thenReturn("e1");
    when(links.getByUser("alice")).thenReturn(Optional.of(binding));
    var employee = mock(Subject.class); when(employee.getName()).thenReturn("张三");
    when(subjects.getByTypeAndId("employee", "e1")).thenReturn(Optional.of(employee));
    var view = service.view("alice", Audience.SELF);
    assertEquals("张三", ((Map<?, ?>) view.get("values")).get("formalName"));
    assertEquals("张三", ((Map<?, ?>) view.get("values")).get("displayName"));
    var fields = (List<?>) view.get("fields");
    for (Object item : fields) {
      var field = (Map<?, ?>) item;
      if (Set.of("formalName", "displayName").contains(field.get("name"))) {
        assertEquals(false, field.get("editable"));
        assertEquals("READ_ONLY", field.get("writeMode"));
      }
    }
    for (String name : List.of("formalName", "displayName")) {
      assertThrows(RuntimeException.class, () -> service.patch("alice", Audience.ADMIN,
          new UserProfileService.Patch("rules", 0L, Map.of(name, "伪造姓名"))));
    }
    verify(users, never()).saveProfile(anyString(), anyString(), anyLong(), anyMap());
  }

  @Test
  void rejectsStaleRulesRevisionsUnknownAndProtectedFieldsBeforeSaving() {
    assertThrows(RuntimeException.class, () -> service.patch("alice", Audience.SELF,
        new UserProfileService.Patch("old", 0L, Map.of("nickname", "x"))));
    assertThrows(RuntimeException.class, () -> service.patch("alice", Audience.SELF,
        new UserProfileService.Patch("rules", 9L, Map.of("nickname", "x"))));
    for (String field : List.of("email", "phoneNumber", "avatar", "emailVerified", "roles", "formalName", "displayName", "unknown")) {
      assertThrows(RuntimeException.class, () -> service.patch("alice", Audience.SELF,
          new UserProfileService.Patch("rules", 0L, Map.of(field, "x"))));
    }
    assertEquals(0L, account.getProfileRevision()); assertNull(account.getNickname());
  }

  @Test
  void refusesAmbiguousExternalDisabledAndUnmigratedAccounts() {
    when(center.getUserCandidates("alice")).thenReturn(List.of(mock(RbacUserModel.class), mock(User.class)));
    assertThrows(RuntimeException.class, () -> service.account("alice"));
    when(center.getUserCandidates("alice")).thenReturn(List.of(mock(User.class)));
    assertThrows(RuntimeException.class, () -> service.account("alice"));
    when(center.getUserCandidates("alice")).thenReturn(List.of(mock(RbacUserModel.class)));
    account.setEnabled(false); assertThrows(RuntimeException.class, () -> service.account("alice"));
    account.setEnabled(true); account.setPublicSubjectId(null);
    assertThrows(RuntimeException.class, () -> service.account("alice"));
  }

  @Test
  void enforcesAdminAndSelfFieldRulesAndKeepsHistoricalExtensionValues() {
    var definitions = new java.util.ArrayList<>(UserProfilePolicy.defaults().fields());
    definitions.add(new UserProfilePolicy.Field("customAlias", "昵称", "string", false,
        Set.of(Audience.SELF, Audience.ADMIN), Set.of(Audience.ADMIN), Map.of("maxLength", 8)));
    when(policies.current()).thenReturn(new UserProfilePolicySource.Snapshot("rules",
        new UserProfilePolicy(definitions, Map.of("email", "NONE", "phone", "NONE"))));
    assertThrows(RuntimeException.class, () -> service.patch("alice", Audience.SELF,
        new UserProfileService.Patch("rules", 0L, Map.of("customAlias", "用户不可改"))));
    doAnswer(call -> {
      Map<?, ?> patch = call.getArgument(3);
      assertEquals(Map.of("historical", "preserve-me", "customAlias", "管理员"), patch.get("profileValues"));
      account.setProfileValues(Map.of("historical", "preserve-me", "customAlias", "管理员"));
      account.setProfileRevision(1L); return null;
    }).when(users).saveProfile(anyString(), anyString(), anyLong(), anyMap());
    service.patch("alice", Audience.ADMIN, new UserProfileService.Patch("rules", 0L, Map.of("customAlias", "管理员")));
    assertEquals("管理员", ((Map<?, ?>) service.view("alice", Audience.SELF).get("values")).get("customAlias"));
  }
}
