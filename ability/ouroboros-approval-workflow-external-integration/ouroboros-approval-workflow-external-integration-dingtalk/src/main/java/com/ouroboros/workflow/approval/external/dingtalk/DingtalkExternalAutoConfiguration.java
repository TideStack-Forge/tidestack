package com.ouroboros.workflow.approval.external.dingtalk;

import com.ouroboros.enterprise.dingtalk.DingtalkAuthProviderBuilder;

import com.ouroboros.enterprise.dingtalk.DingtalkTokenCache;

import com.ouroboros.enterprise.dingtalk.DingtalkEnterpriseAuthProfileProvider;

import com.ouroboros.enterprise.dingtalk.DingtalkConnectionCredentialMapper;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.workflow.approval.external.runtime.identity.ConnectionScopedIdentityResolver;

/**
 * 钉钉适配器模块自动配置。
 *
 * <p>Provider 以 Bean 注册（注入身份映射器与 HTTP 客户端），Registry 的
 * ProviderBeanBridge 会收集 Bean Provider（决策日志 D-7：替代 SPI 注册，
 * 因适配器需要注入 DingtalkUserIdentityMapper）。
 */
@Configuration
@DataModelScan
@ComponentScan
@Import(ConnectionScopedIdentityResolver.class)
public class DingtalkExternalAutoConfiguration {

  @Bean
  DingtalkEngineAdapterProvider dingtalkEngineAdapterProvider(
      DingtalkUserIdentityMapper identityMapper,
      @Qualifier("dingtalkRestTemplate") RestTemplate dingtalkRestTemplate,
      @Value("${ouroboros.workflow.approval.external.dingtalk.http.overall-timeout-ms:15000}")
      long overallTimeoutMillis,
      DingtalkTokenCache tokens) {
    return new DingtalkEngineAdapterProvider(
        identityMapper, dingtalkRestTemplate, tokens, overallTimeoutMillis);
  }







  @Bean
  RestTemplate dingtalkRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.workflow.approval.external.dingtalk.http.connect-timeout-ms:5000}")
      int connectTimeoutMillis,
      @Value("${ouroboros.workflow.approval.external.dingtalk.http.read-timeout-ms:10000}")
      int readTimeoutMillis) {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(Math.max(connectTimeoutMillis, 1));
    requestFactory.setReadTimeout(Math.max(readTimeoutMillis, 1));
    var http = new RestTemplate(requestFactory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("dingtalk");
      return execution.execute(request, body);
    });
    return http;
  }



}
