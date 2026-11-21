package com.ouroboros.enterprise.feishu;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Only provider protocol/auth beans; no organization mapper, workflow models or workers. */
@Configuration(proxyBeanMethods = false)
public class FeishuConnectorAutoConfiguration {
  @Bean
  FeishuCallbackProtocol feishuCallbackProtocol() { return new FeishuCallbackProtocol(); }
  @Bean
  FeishuConnectionCredentialMapper feishuConnectionCredentialMapper() { return new FeishuConnectionCredentialMapper(); }
  @Bean
  FeishuEnterpriseAuthProfileProvider feishuEnterpriseAuthProfileProvider() { return new FeishuEnterpriseAuthProfileProvider(); }
  @Bean
  FeishuTokenCache feishuTokenCache() { return new FeishuTokenCache(); }
  @Bean
  FeishuApplicationTokens feishuApplicationTokens(
      @Qualifier("feishuAuthRestTemplate") RestTemplate http, FeishuTokenCache tokens) {
    return new FeishuApplicationTokens(http, tokens);
  }
  @Bean("feishuAuthRestTemplate")
  RestTemplate feishuAuthRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.security.external-auth.feishu.http.connect-timeout-ms:5000}") int connectTimeout,
      @Value("${ouroboros.security.external-auth.feishu.http.read-timeout-ms:10000}") int readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.max(connectTimeout, 1)); factory.setReadTimeout(Math.max(readTimeout, 1));
    var http = new RestTemplate(factory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("feishu");
      return execution.execute(request, body);
    });
    return http;
  }
}
