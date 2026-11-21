package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomAuthProviderBuilder;

import com.ouroboros.enterprise.wecom.WecomEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.wecom.WecomConnectionCredentialMapper;

import com.ouroboros.enterprise.wecom.WecomCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

class WecomConfigurationContractTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(WecomExternalAutoConfiguration.class,
          com.ouroboros.enterprise.wecom.WecomConnectorAutoConfiguration.class)
      .withBean(EnterpriseIdentityMappingService.class,
          () -> mock(EnterpriseIdentityMappingService.class))
      .withBean(ConnectionAwareAdapterResolver.class,
          () -> mock(ConnectionAwareAdapterResolver.class))
      .withBean(ExternalCallbackReceiptService.class,
          () -> mock(ExternalCallbackReceiptService.class));

  @Test
  void credentialMapperCreatesTypedRedactedCredential() {
    WecomConnectionCredentialMapper mapper = new WecomConnectionCredentialMapper();

    WecomCredential credential = (WecomCredential) mapper.map("""
        {"suiteId":"suite-1","suiteSecret":"suite-secret","suiteTicket":"suite-ticket",
         "permanentCode":"permanent-code","callbackToken":"callback-token",
         "encodingAesKey":"encoding-key"}
        """);

    assertThat(mapper.provider()).isEqualTo("wecom");
    assertThat(credential.suiteId()).isEqualTo("suite-1");
    assertThat(credential.toString()).doesNotContain("suite-secret")
        .doesNotContain("permanent-code")
        .doesNotContain("callback-token");
    assertThatThrownBy(() -> mapper.map("{\"suiteId\":\"suite-1\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("suiteSecret");
  }

  @Test
  void identityMappingIsConnectionScopedInBothDirections() {
    EnterpriseIdentityMappingService mappingService = mock(EnterpriseIdentityMappingService.class);
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.of("wecom-1"));
    when(mappingService.toExternalMember("conn-2", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.of("wecom-2"));
    when(mappingService.toPlatform("conn-2", "wecom-1"))
        .thenReturn(Optional.empty());
    WecomUserIdentityMapper mapper = new WecomUserIdentityMapper(
        new ConnectionScopedIdentityResolver(mappingService));

    assertThat(mapper.toExternalUser(
        "platform-1", WecomEngineAdapterProvider.ADAPTER_ID, "conn-1"))
        .contains("wecom-1");
    assertThat(mapper.toExternalUser(
        "platform-1", WecomEngineAdapterProvider.ADAPTER_ID, "conn-2"))
        .contains("wecom-2");
    assertThat(mapper.toPlatformUser(
        "wecom-1", WecomEngineAdapterProvider.ADAPTER_ID, "conn-2"))
        .isEmpty();
    assertThat(mapper.toExternalUser("platform-1", "another-adapter", "conn-1"))
        .isEmpty();
  }

  @Test
  void configuredClientRejectsBeforeTransportWhenSharedBudgetIsExhausted() {
    WecomExternalAutoConfiguration configuration = new WecomExternalAutoConfiguration();

    var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
    var budget = org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class);
    org.mockito.Mockito.doThrow(new com.ouroboros.enterprise.EnterpriseApiRateLimitException("budget depleted"))
        .when(budget).acquire("wecom");
    beans.addBean("budget", budget);
    var restTemplate = configuration.wecomApprovalRestTemplate(
        beans.getBeanProvider(com.ouroboros.enterprise.EnterpriseApiBudget.class), 2_000, 4_000);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> restTemplate.getForObject("http://127.0.0.1:1/unused", String.class))
        .isInstanceOf(com.ouroboros.enterprise.EnterpriseApiRateLimitException.class);
    org.mockito.Mockito.verify(budget).acquire("wecom");
  }

  @Test
  void autoConfigurationRegistersCompleteProviderIngress() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(WecomEngineAdapterProvider.class);
      assertThat(context).hasSingleBean(WecomConnectionCredentialMapper.class);
      assertThat(context).hasSingleBean(WecomEnterpriseAuthProfileProvider.class);
      assertThat(context).hasSingleBean(WecomAuthProviderBuilder.class);
      assertThat(context).hasSingleBean(WecomUserIdentityMapper.class);
      assertThat(context).hasSingleBean(WecomApprovalCallbackSubscriber.class);
      assertThat(context).hasBean("wecomApprovalRestTemplate");
      assertThat(context).hasBean("wecomAuthRestTemplate");
    });
  }

  @Test
  void connectionConfigurationIsTheOnlyAuthEnablementControl() {
    contextRunner
        .withPropertyValues("ouroboros.security.external-auth.wecom.enabled=false")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(WecomEnterpriseAuthProfileProvider.class);
          assertThat(context).hasSingleBean(WecomAuthProviderBuilder.class);
          assertThat(context).hasBean("wecomAuthRestTemplate");
        });
  }

}
