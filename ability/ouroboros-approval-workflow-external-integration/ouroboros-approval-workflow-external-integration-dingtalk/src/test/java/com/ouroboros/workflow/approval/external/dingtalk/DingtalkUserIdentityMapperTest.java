package com.ouroboros.workflow.approval.external.dingtalk;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/**
 * M17 钉钉身份映射测试：共享 SSO 绑定命中/未命中、adapterId 作用域过滤。
 */
class DingtalkUserIdentityMapperTest {

  private EnterpriseIdentityMappingService mappingService;
  private DingtalkUserIdentityMapper mapper;

  @BeforeEach
  void setUp() {
    mappingService = mock(EnterpriseIdentityMappingService.class);
    mapper = new DingtalkUserIdentityMapper(
        new ConnectionScopedIdentityResolver(mappingService));
  }

  @Test
  void resolvesPlatformUserFromConfiguredMappings() {
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("user-1")))
        .thenReturn(Optional.of("ding-1"));
    when(mappingService.toPlatform("conn-1", "ding-2"))
        .thenReturn(Optional.of(new PlatformPrincipalRef("user-2")));

    assertThat(mapper.toPlatformUser("ding-2", "dingtalk-oa", "conn-1")).contains("user-2");
    assertThat(mapper.toExternalUser("user-1", "dingtalk-oa", "conn-1")).contains("ding-1");
  }

  @Test
  void unresolvedMappingReturnsEmpty() {
    when(mappingService.toPlatform("conn-1", "ding-999"))
        .thenReturn(Optional.empty());

    assertThat(mapper.toPlatformUser("ding-999", "dingtalk-oa", "conn-1")).isEmpty();
  }

  @Test
  void otherAdapterScopedEmpty() {
    assertThat(mapper.toPlatformUser("ding-1", "other-adapter", "conn-1")).isEmpty();
    assertThat(mapper.toExternalUser("user-1", "other-adapter", "conn-1")).isEmpty();
  }

  @Test
  void missingConfigYieldsEmpty() {
    when(mappingService.toPlatform("conn-1", "ding-1"))
        .thenReturn(Optional.empty());

    assertThat(mapper.toPlatformUser("ding-1", "dingtalk-oa", "conn-1")).isEmpty();
  }

  @Test
  void mappingFromAnotherConnectionIsNotUsed() {
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("user-1")))
        .thenReturn(Optional.empty());
    when(mappingService.toExternalMember("conn-2", new PlatformPrincipalRef("user-1")))
        .thenReturn(Optional.of("ding-1"));

    assertThat(mapper.toExternalUser("user-1", "dingtalk-oa", "conn-1")).isEmpty();
    assertThat(mapper.toExternalUser("user-1", "dingtalk-oa", "conn-2")).contains("ding-1");
  }

}
