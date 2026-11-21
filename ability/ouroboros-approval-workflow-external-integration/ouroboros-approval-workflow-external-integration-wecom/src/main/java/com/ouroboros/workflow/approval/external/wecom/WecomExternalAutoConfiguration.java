package com.ouroboros.workflow.approval.external.wecom;

import com.ouroboros.enterprise.wecom.WecomAuthProviderBuilder;

import com.ouroboros.enterprise.wecom.WecomTokenCache;

import com.ouroboros.enterprise.wecom.WecomEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.wecom.WecomConnectionCredentialMapper;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/** Spring registration for the Enterprise WeChat approval provider. */
@Configuration
@ComponentScan
@Import(ConnectionScopedIdentityResolver.class)
public class WecomExternalAutoConfiguration {

  @Bean
  WecomEngineAdapterProvider wecomEngineAdapterProvider(
      WecomUserIdentityMapper identityMapper,
      @Qualifier("wecomApprovalRestTemplate") RestTemplate restTemplate,
      @Value("${ouroboros.workflow.approval.external.wecom.http.overall-timeout-ms:15000}")
      long overallTimeoutMillis,
      WecomTokenCache tokens) {
    return new WecomEngineAdapterProvider(
        identityMapper, restTemplate, tokens, overallTimeoutMillis);
  }







  @Bean("wecomApprovalRestTemplate")
  RestTemplate wecomApprovalRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.workflow.approval.external.wecom.http.connect-timeout-ms:5000}")
      int connectTimeoutMillis,
      @Value("${ouroboros.workflow.approval.external.wecom.http.read-timeout-ms:10000}")
      int readTimeoutMillis) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(connectTimeoutMillis, 1));
    requestFactory.setReadTimeout(Math.max(readTimeoutMillis, 1));
    var http = new RestTemplate(requestFactory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("wecom");
      return execution.execute(request, body);
    });
    return http;
  }


}
