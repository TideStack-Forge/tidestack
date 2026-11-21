package com.ouroboros.security.rbac.profile;

import com.ouroboros.security.rbac.profile.service.ContactChangeService;

import com.ouroboros.security.rbac.profile.service.UserProfileService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import com.ouroboros.cache.MemoryCacheManager;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.profile.*;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;

class ContactChangeServiceTest {
  final UserProfileService profiles = mock(UserProfileService.class);
  final UserProfilePolicySource policies = mock(UserProfilePolicySource.class);
  final UserService users = mock(UserService.class);
  final UserPasswordVerifier passwords = mock(UserPasswordVerifier.class);
  final MemoryCacheManager cache = new MemoryCacheManager();
  final ContactVerificationDelivery delivery = mock(ContactVerificationDelivery.class);
  UserEntity account;
  ContactVerificationDelivery.Message delivered;
  ContactChangeService service;

  @BeforeEach
  void setUp() {
    account = new UserEntity(); account.setUserIdentity("alice"); account.setPublicSubjectId("65eb15bb-becd-4f68-a69e-068f951c4507");
    account.setProfileRevision(0L); account.setEmail("old@example.com"); account.setEmailVerified(true);
    when(profiles.account("alice")).thenReturn(account);
    when(passwords.verify("alice", "current-password")).thenReturn(true);
    when(delivery.supports(anyString())).thenReturn(true);
    doAnswer(call -> { delivered = call.getArgument(0); return null; }).when(delivery).deliver(any());
    rules("REQUIRED");
    var beans = new DefaultListableBeanFactory(); beans.registerSingleton("delivery", delivery);
    beans.registerSingleton("proof", new ContactVerificationProof() {
      public boolean ready() { return true; }
      public String digest(String id, String value) {
        try { return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
            .digest((id + ":" + value).getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
      }
    });
    service = new ContactChangeService(profiles, policies, users, passwords, cache,
        beans.getBeanProvider(ContactVerificationDelivery.class), beans.getBeanProvider(ContactVerificationProof.class));
    doAnswer(call -> {
      assertSame(SystemSecurityIdentities.system(), CurrentSecurityContextHolder.getRequiredIdentity());
      assertEquals(account.getProfileRevision(), call.getArgument(2));
      Map<?, ?> patch = call.getArgument(3);
      if (patch.containsKey("email")) { account.setEmail((String) patch.get("email")); account.setEmailVerified((Boolean) patch.get("emailVerified")); }
      if (patch.containsKey("phoneNumber")) { account.setPhoneNumber((String) patch.get("phoneNumber")); account.setPhoneNumberVerified((Boolean) patch.get("phoneNumberVerified")); }
      account.setProfileRevision(account.getProfileRevision() + 1); return null;
    }).when(users).saveProfile(anyString(), anyString(), anyLong(), anyMap());
  }

  private void rules(String mode) {
    when(policies.current()).thenReturn(new UserProfilePolicySource.Snapshot("rules",
        new UserProfilePolicy(UserProfilePolicy.defaults().fields(), Map.of("email", mode, "phone", mode))));
  }
  private <T> T asUser(java.util.function.Supplier<T> action) {
    User user = new User() { public String getUserIdentity() { return "alice"; } };
    return CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user), action);
  }
  private ContactChangeService.Change change(String value) { return new ContactChangeService.Change("rules", account.getProfileRevision(), value, "current-password"); }

  @Test
  void requiredPreservesOldContactUntilBoundOneTimeProofSucceeds() {
    asUser(() -> {
      var pending = service.change("session-a", "source", "email", change("New@EXAMPLE.com"));
      assertEquals("PENDING", pending.get("status")); assertEquals("old@example.com", account.getEmail());
      assertTrue(account.getEmailVerified()); assertEquals(0L, account.getProfileRevision());
      assertFalse(pending.toString().contains(delivered.proof()));
      var status = service.status("session-a", "email"); assertEquals(pending.get("requestId"), status.get("requestId"));
      assertEquals("APPLIED", service.confirm("session-a", "email", delivered.requestId(), delivered.proof()).get("status"));
      assertEquals("New@example.com", account.getEmail()); assertTrue(account.getEmailVerified()); assertEquals(1L, account.getProfileRevision());
      assertThrows(RuntimeException.class, () -> service.confirm("session-a", "email", delivered.requestId(), delivered.proof())); return null;
    });
  }

  @Test
  void noneChangesAndClearsContactWithoutReusingOldVerification() {
    rules("NONE");
    asUser(() -> {
      service.change("session", "source", "email", change("new@example.com"));
      assertEquals("new@example.com", account.getEmail()); assertFalse(account.getEmailVerified());
      long revision = account.getProfileRevision();
      assertEquals("UNCHANGED", service.change("session", "source", "email", change("new@example.com")).get("status"));
      assertEquals(revision, account.getProfileRevision());
      service.change("session", "source", "email", change(null)); assertNull(account.getEmail()); assertFalse(account.getEmailVerified());
      assertNull(delivered); return null;
    });
  }

  @Test
  void rejectsWrongSessionStaleRevisionAndRepeatedProofGuesses() {
    asUser(() -> {
      service.change("session-a", "source", "phone", change("+8613800000000"));
      var message = delivered;
      assertThrows(RuntimeException.class, () -> service.confirm("session-b", "phone", message.requestId(), message.proof()));
      for (int i = 0; i < 5; i++) {
        assertThrows(RuntimeException.class, () -> service.confirm("session-a", "phone", message.requestId(), "wrong"));
      }
      assertThrows(RuntimeException.class, () -> service.confirm("session-a", "phone", message.requestId(), message.proof()));
      assertNull(account.getPhoneNumber()); assertEquals(0L, account.getProfileRevision()); return null;
    });
  }

  @Test
  void failedDeliveryAndSchemaChangesNeverChangeEffectiveContact() {
    asUser(() -> {
      doThrow(new IllegalStateException("provider contains secret")).when(delivery).deliver(any());
      var error = assertThrows(RuntimeException.class, () -> service.change("session", "source", "email", change("new@example.com")));
      assertFalse(error.getMessage().contains("provider contains secret")); assertEquals("old@example.com", account.getEmail());
      assertEquals("NONE", service.status("session", "email").get("status"));
      doAnswer(call -> { delivered = call.getArgument(0); return null; }).when(delivery).deliver(any());
      service.change("session", "source", "phone", change("+8613800000000"));
      when(policies.current()).thenReturn(new UserProfilePolicySource.Snapshot("new-rules", UserProfilePolicy.defaults()));
      assertThrows(RuntimeException.class, () -> service.confirm("session", "phone", delivered.requestId(), delivered.proof()));
      assertNull(account.getPhoneNumber()); return null;
    });
  }

  @Test
  void cancellationAndSendCooldownRejectOldChallengesAndRapidResends() {
    asUser(() -> {
      service.change("session", "source", "email", change("new@example.com"));
      var message = delivered;
      assertThrows(RuntimeException.class, () -> service.change("session", "source", "email", change("other@example.com")));
      service.cancel("session", "email", message.requestId());
      assertThrows(RuntimeException.class, () -> service.confirm("session", "email", message.requestId(), message.proof()));
      assertEquals("old@example.com", account.getEmail()); return null;
    });
  }
  @Test
  void ordinaryUserCannotReplaceAnotherAccountsContact() {
    asUser(() -> {
      assertThrows(RuntimeException.class, () -> service.replaceByAdmin("alice", "email",
          new ContactChangeService.Replacement("rules", 0L, "replacement@example.com")));
      assertEquals("old@example.com", account.getEmail());
      assertTrue(account.getEmailVerified()); return null;
    });
  }

  @Test
  void administratorReplacementClearsProofAndInvalidatesPendingRequest() {
    asUser(() -> service.change("session", "source", "email", change("pending@example.com")));
    var original = delivered;
    var definition = new com.ouroboros.security.authority.BaseAuthority();
    definition.setFullName("systemSetting.userManagement.profile");
    var grant = mock(com.ouroboros.security.authority.OuroborosGrantedAuthority.class);
    when(grant.getAuthority()).thenReturn(definition.getFullName());
    when(grant.getAccessControlParams()).thenReturn(com.ouroboros.security.authority.accesscontrol.AccessControlParams.empty());
    var role = mock(com.ouroboros.security.rbac.Role.class);
    doReturn(List.of(grant)).when(role).getAuthorities();
    User admin = new User() {
      public String getUserIdentity() { return "administrator"; }
      public List<com.ouroboros.security.rbac.Role> getRoles() { return List.of(role); }
    };
    try (var registry = mockStatic(com.ouroboros.security.authority.AuthorityCenter.class)) {
      registry.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority(definition.getFullName()))
          .thenReturn(Optional.of(definition));
      CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(admin), () -> {
        assertEquals("APPLIED", service.replaceByAdmin("alice", "email",
            new ContactChangeService.Replacement("rules", 0L, "replacement@example.com")).get("status"));
        assertThrows(RuntimeException.class, () -> service.replaceByAdmin("alice", "email",
            new ContactChangeService.Replacement("rules", 0L, "stale@example.com")));
        return null;
      });
    }
    assertEquals("replacement@example.com", account.getEmail());
    assertFalse(account.getEmailVerified());
    asUser(() -> {
      assertThrows(RuntimeException.class, () -> service.confirm("session", "email", original.requestId(), original.proof()));
      return null;
    });
  }

}
