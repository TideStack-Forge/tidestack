package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkAuthProviderBuilder;

import com.ouroboros.enterprise.dingtalk.DingtalkEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.dingtalk.DingtalkConnectionCredentialMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.ouroboros.security.identity.mapping.EnterpriseIdentityMappingService;
import com.ouroboros.workflow.approval.external.runtime.ConnectionAwareAdapterResolver;
import com.ouroboros.workflow.approval.external.runtime.event.ExternalCallbackReceiptService;

class DingtalkConfigurationContractTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(DingtalkExternalAutoConfiguration.class,
          com.ouroboros.enterprise.dingtalk.DingtalkConnectorAutoConfiguration.class)
      .withBean(EnterpriseIdentityMappingService.class,
          () -> mock(EnterpriseIdentityMappingService.class))
      .withBean(ConnectionAwareAdapterResolver.class,
          () -> mock(ConnectionAwareAdapterResolver.class))
      .withBean(ExternalCallbackReceiptService.class,
          () -> mock(ExternalCallbackReceiptService.class));

  @Test
  void authFacetIsEnabledByDefault() {
    contextRunner.run(context -> {
      assertThat(context).hasNotFailed();
      assertThat(context).hasSingleBean(DingtalkEngineAdapterProvider.class);
      assertThat(context).hasSingleBean(DingtalkConnectionCredentialMapper.class);
      assertThat(context).hasSingleBean(DingtalkEnterpriseAuthProfileProvider.class);
      assertThat(context).hasSingleBean(DingtalkAuthProviderBuilder.class);
      assertThat(context).hasBean("dingtalkRestTemplate");
      assertThat(context).hasBean("dingtalkAuthRestTemplate");
    });
  }

  @Test
  void connectionConfigurationIsTheOnlyAuthEnablementControl() {
    contextRunner
        .withPropertyValues("ouroboros.security.external-auth.dingtalk.enabled=false")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(DingtalkEnterpriseAuthProfileProvider.class);
          assertThat(context).hasSingleBean(DingtalkAuthProviderBuilder.class);
          assertThat(context).hasBean("dingtalkAuthRestTemplate");
        });
  }

}
