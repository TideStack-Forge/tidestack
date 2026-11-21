package com.ouroboros.enterprise.dingtalk;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Only provider protocol/auth beans; no organization mapper, workflow models or workers. */
@Configuration(proxyBeanMethods = false)
public class DingtalkConnectorAutoConfiguration {
  @Bean
  DingtalkCallbackProtocol dingtalkCallbackProtocol() { return new DingtalkCallbackProtocol(); }
  @Bean
  DingtalkConnectionCredentialMapper dingtalkConnectionCredentialMapper() { return new DingtalkConnectionCredentialMapper(); }
  @Bean
  DingtalkEnterpriseAuthProfileProvider dingtalkEnterpriseAuthProfileProvider() { return new DingtalkEnterpriseAuthProfileProvider(); }
  @Bean
  DingtalkTokenCache dingtalkTokenCache() { return new DingtalkTokenCache(); }
  @Bean
  DingtalkAuthProviderBuilder dingtalkAuthProviderBuilder(@Qualifier("dingtalkAuthRestTemplate") RestTemplate http, DingtalkTokenCache tokens,
      @Value("${ouroboros.security.external-auth.dingtalk.http.overall-timeout-ms:15000}") long overallTimeoutMillis) {
    return new DingtalkAuthProviderBuilder(http, tokens, overallTimeoutMillis);
  }
  @Bean
  DingtalkApplicationTokens dingtalkApplicationTokens(
      @Qualifier("dingtalkAuthRestTemplate") RestTemplate http, DingtalkTokenCache tokens) {
    return new DingtalkApplicationTokens(http, tokens);
  }
  @Bean("dingtalkAuthRestTemplate")
  RestTemplate dingtalkAuthRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.security.external-auth.dingtalk.http.connect-timeout-ms:5000}") int connectTimeout,
      @Value("${ouroboros.security.external-auth.dingtalk.http.read-timeout-ms:10000}") int readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.max(connectTimeout, 1)); factory.setReadTimeout(Math.max(readTimeout, 1));
    var http = new RestTemplate(factory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("dingtalk");
      return execution.execute(request, body);
    });
    return http;
  }
}
