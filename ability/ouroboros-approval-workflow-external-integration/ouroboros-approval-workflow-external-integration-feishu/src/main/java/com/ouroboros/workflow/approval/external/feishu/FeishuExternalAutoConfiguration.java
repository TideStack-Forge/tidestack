package com.ouroboros.workflow.approval.external.feishu;

import com.ouroboros.enterprise.feishu.FeishuTokenCache;

import com.ouroboros.enterprise.feishu.FeishuEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.feishu.FeishuConnectionCredentialMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/** Spring registration for the Feishu native approval provider. */
@Configuration
@ComponentScan
@Import(ConnectionScopedIdentityResolver.class)
public class FeishuExternalAutoConfiguration {

  @Bean
  FeishuEngineAdapterProvider feishuEngineAdapterProvider(
      FeishuUserIdentityMapper identityMapper,
      @Qualifier("feishuApprovalRestTemplate") RestTemplate restTemplate,
      @Value("${ouroboros.workflow.approval.external.feishu.http.overall-timeout-ms:15000}")
      long overallTimeoutMillis,
      FeishuTokenCache tokens) {
    return new FeishuEngineAdapterProvider(
        identityMapper, restTemplate, tokens, overallTimeoutMillis);
  }





  @Bean("feishuApprovalRestTemplate")
  RestTemplate feishuApprovalRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.workflow.approval.external.feishu.http.connect-timeout-ms:5000}")
      int connectTimeoutMillis,
      @Value("${ouroboros.workflow.approval.external.feishu.http.read-timeout-ms:10000}")
      int readTimeoutMillis) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(connectTimeoutMillis, 1));
    requestFactory.setReadTimeout(Math.max(readTimeoutMillis, 1));
    var http = new RestTemplate(requestFactory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("feishu");
      return execution.execute(request, body);
    });
    return http;
  }
}
