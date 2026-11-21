package com.ouroboros.security.rbac.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;

class UserSsoEnterpriseIdentityMappingServiceTest {

  @Test void directoryAssociationsUseTheSamePortAndConflictsAreNotResolvedByInjectionOrder() {
    var sso = mock(UserSsoService.class);
    var directory = mock(com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingContributor.class);
    var service = new UserSsoEnterpriseIdentityMappingService(sso, List.of(directory));
    when(directory.toPlatform("conn", "member")).thenReturn(Optional.of(new PlatformPrincipalRef("user")));
    when(directory.toExternalMember("conn", new PlatformPrincipalRef("user"))).thenReturn(Optional.of("member"));
    assertThat(service.toPlatform("conn", "member")).contains(new PlatformPrincipalRef("user"));
    assertThat(service.toExternalMember("conn", new PlatformPrincipalRef("user"))).contains("member");
    when(sso.findByChannelAndMemberId("conn", "member"))
        .thenReturn(Optional.of(binding("other-user", "conn", "login", "member")));
    assertThatThrownBy(() -> service.toPlatform("conn", "member")).hasMessageContaining("冲突");
    when(sso.findByUserIdentity("user")).thenReturn(List.of(binding("user", "conn", "login", "another-member")));
    assertThatThrownBy(() -> service.toExternalMember("conn", new PlatformPrincipalRef("user"))).hasMessageContaining("多个企业成员");
  }

  @Test void inactiveDirectoryAssociationCannotFallBackToAnOlderSsoMember() {
    var sso = mock(UserSsoService.class);
    var directory = mock(com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingContributor.class);
    var service = new UserSsoEnterpriseIdentityMappingService(sso, List.of(directory));
    when(sso.findByChannelAndMemberId("conn", "member"))
        .thenReturn(Optional.of(binding("user", "conn", "login", "member")));
    when(directory.toPlatform("conn", "member")).thenThrow(new com.ouroboros.exception.ValidationException("成员关系已失效"));
    assertThatThrownBy(() -> service.toPlatform("conn", "member")).hasMessageContaining("已失效");
  }

  private UserSsoService userSsoService;
  private UserSsoEnterpriseIdentityMappingService mappingService;

  @BeforeEach
  void setUp() {
    userSsoService = mock(UserSsoService.class);
    mappingService = new UserSsoEnterpriseIdentityMappingService(userSsoService);
  }

  @Test
  void resolvesMemberIdentityToPlatformPrincipal() {
    when(userSsoService.findByChannelAndMemberId("connection-1", "member-1"))
        .thenReturn(Optional.of(binding("platform-1", "connection-1", "external-1", "member-1")));

    assertThat(mappingService.toPlatform("connection-1", "member-1"))
        .contains(new PlatformPrincipalRef("platform-1"));
    verify(userSsoService).findByChannelAndMemberId("connection-1", "member-1");
  }

  @Test
  void resolvesPlatformPrincipalToMemberIdentityWithinConnectionScope() {
    when(userSsoService.findByUserIdentity("platform-1")).thenReturn(List.of(
        binding("platform-1", "connection-1", "external-1", "member-1"),
        binding("platform-1", "connection-2", "external-2", "member-2")));

    assertThat(mappingService.toExternalMember(
        "connection-1", new PlatformPrincipalRef("platform-1")))
        .contains("member-1");
    assertThat(mappingService.toExternalMember(
        "connection-2", new PlatformPrincipalRef("platform-1")))
        .contains("member-2");
  }

  @Test
  void doesNotFallBackToTheLoginIdentityWhenMemberIdentityIsMissing() {
    when(userSsoService.findByUserIdentity("platform-1")).thenReturn(List.of(
        binding("platform-1", "connection-1", "external-1", null)));

    assertThat(mappingService.toExternalMember(
        "connection-1", new PlatformPrincipalRef("platform-1"))).isEmpty();
  }

  @Test
  void ambiguousMemberBindingsFailClosed() {
    when(userSsoService.findByUserIdentity("platform-1")).thenReturn(List.of(
        binding("platform-1", "connection-1", "external-1", "member-1"),
        binding("platform-1", "connection-1", "external-2", "member-2")));

    assertThat(mappingService.toExternalMember(
        "connection-1", new PlatformPrincipalRef("platform-1"))).isEmpty();
  }

  @Test
  void invalidIdentifiersAreRejectedBeforeQuery() {
    assertThat(mappingService.toPlatform("", "member-1")).isEmpty();
    assertThat(mappingService.toPlatform("connection-1", " ")).isEmpty();
    assertThat(mappingService.toExternalMember("connection-1", null)).isEmpty();
    assertThat(mappingService.toExternalMember(
        "connection-1", new PlatformPrincipalRef(" "))).isEmpty();
  }

  private UserSsoEntity binding(String platformUserId, String connectionId, String externalUserId,
                                String memberId) {
    UserSsoEntity binding = new UserSsoEntity();
    binding.setUserIdentity(platformUserId);
    binding.setSsoChannel(connectionId);
    binding.setSsoUserIdentity(externalUserId);
    binding.setExternalAttributes(memberId == null
        ? Map.of()
        : Map.of(ExternalIdentityAttributes.MEMBER_ID, memberId));
    return binding;
  }
}
