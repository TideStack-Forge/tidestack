package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.feishu.FeishuConnectionCredentialMapper;

import com.ouroboros.enterprise.feishu.FeishuCredential;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.externalauth.EnterpriseAuthProfileResolver;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.config.OAuth2ProviderSpec;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.security.identity.mapping.PlatformPrincipalRef;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

class FeishuConfigurationContractTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(FeishuExternalAutoConfiguration.class,
          com.ouroboros.enterprise.feishu.FeishuConnectorAutoConfiguration.class)
      .withBean(EnterpriseIdentityMappingService.class,
          () -> mock(EnterpriseIdentityMappingService.class))
      .withBean(ConnectionAwareAdapterResolver.class,
          () -> mock(ConnectionAwareAdapterResolver.class))
      .withBean(ExternalCallbackReceiptService.class,
          () -> mock(ExternalCallbackReceiptService.class))
      .withBean(CompositeSecretResolver.class,
          () -> mock(CompositeSecretResolver.class));

  @Test
  void credentialMapperCreatesTypedRedactedCredential() {
    FeishuConnectionCredentialMapper mapper = new FeishuConnectionCredentialMapper();

    FeishuCredential credential = (FeishuCredential) mapper.map("""
        {"appId":"app-id","appSecret":"app-secret",
         "verificationToken":"verification-token","encryptKey":"encrypt-key"}
        """);

    assertThat(mapper.provider()).isEqualTo("feishu");
    assertThat(credential.appId()).isEqualTo("app-id");
    assertThat(credential.toString()).doesNotContain("app-secret")
        .doesNotContain("verification-token")
        .doesNotContain("encrypt-key");
    assertThatThrownBy(() -> mapper.map("{\"appId\":\"app-id\"}"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("appSecret");
  }

  @Test
  void identityMappingIsConnectionScopedInBothDirections() {
    EnterpriseIdentityMappingService mappingService = mock(EnterpriseIdentityMappingService.class);
    when(mappingService.toExternalMember("conn-1", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.of("ou_1"));
    when(mappingService.toExternalMember("conn-2", new PlatformPrincipalRef("platform-1")))
        .thenReturn(Optional.of("ou_2"));
    when(mappingService.toPlatform("conn-2", "ou_1"))
        .thenReturn(Optional.empty());
    FeishuUserIdentityMapper mapper = new FeishuUserIdentityMapper(
        new ConnectionScopedIdentityResolver(mappingService));

    assertThat(mapper.toExternalUser(
        "platform-1", FeishuEngineAdapterProvider.ADAPTER_ID, "conn-1"))
        .contains("ou_1");
    assertThat(mapper.toExternalUser(
        "platform-1", FeishuEngineAdapterProvider.ADAPTER_ID, "conn-2"))
        .contains("ou_2");
    assertThat(mapper.toPlatformUser(
        "ou_1", FeishuEngineAdapterProvider.ADAPTER_ID, "conn-2"))
        .isEmpty();
    assertThat(mapper.toExternalUser("platform-1", "another-adapter", "conn-1"))
        .isEmpty();
  }

  @Test
  void configuredClientRejectsBeforeTransportWhenSharedBudgetIsExhausted() {
    FeishuExternalAutoConfiguration configuration = new FeishuExternalAutoConfiguration();

    var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
    var budget = org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class);
    org.mockito.Mockito.doThrow(new com.ouroboros.enterprise.EnterpriseApiRateLimitException("budget depleted"))
        .when(budget).acquire("feishu");
    beans.addBean("budget", budget);
    var restTemplate = configuration.feishuApprovalRestTemplate(
        beans.getBeanProvider(com.ouroboros.enterprise.EnterpriseApiBudget.class), 2_000, 4_000);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> restTemplate.getForObject("http://127.0.0.1:1/unused", String.class))
        .isInstanceOf(com.ouroboros.enterprise.EnterpriseApiRateLimitException.class);
    org.mockito.Mockito.verify(budget).acquire("feishu");
  }

  @Test
  void autoConfigurationRegistersCompleteProviderIngress() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(FeishuEngineAdapterProvider.class);
      assertThat(context).hasSingleBean(FeishuConnectionCredentialMapper.class);
      assertThat(context).hasSingleBean(FeishuEnterpriseAuthProfileProvider.class);
      assertThat(context).hasSingleBean(FeishuUserIdentityMapper.class);
      assertThat(context).hasSingleBean(FeishuApprovalCallbackSubscriber.class);
      assertThat(context).hasBean("feishuApprovalRestTemplate");
    });
  }

  @Test
  void bridgesPublishedEnterpriseConnectionToOAuthProviderInSpringContext() {
    new ApplicationContextRunner()
        .withUserConfiguration(
            com.ouroboros.workflow.approval.external.bridge.EnterpriseOAuthBridgeTestConfiguration.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(EnterpriseAuthProfileResolver.class);

          var provider = context.getBean(ExternalAuthProviderFactory.class)
              .getRedirectProvider("feishu-main", "oauth2")
              .orElseThrow();
          OAuth2ProviderSpec profile = (OAuth2ProviderSpec) provider.getSpec();

          assertThat(profile.getKey()).isEqualTo("feishu-main");
          assertThat(profile.isConnectionScoped()).isTrue();
          assertThat(profile.getClientIdRef()).isEqualTo("test:feishu-main#/appId");
          assertThat(profile.getClientSecretRef()).isEqualTo("test:feishu-main#/appSecret");
          assertThat(provider.buildAuthorizeUrl("/").getAuthorizeUrl())
              .contains("client_id=cli_context")
              .contains("%2Fapi%2Fauth%2Foauth2%2Ffeishu-main%2Fcallback");
          assertThat(context.getBean(ExternalAuthProviderFactory.class)
              .getRedirectProvider("dingtalk-main", "oauth2")).isEmpty();
          assertThat(context.getBean(ExternalAuthProviderFactory.class)
              .getRedirectProvider("wecom-main", "oauth2")).isEmpty();
        });
  }

}
