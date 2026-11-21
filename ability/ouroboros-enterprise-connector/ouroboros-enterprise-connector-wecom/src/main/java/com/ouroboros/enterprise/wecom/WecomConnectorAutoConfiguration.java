package com.ouroboros.enterprise.wecom;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** Only provider protocol/auth beans; no organization mapper, workflow models or workers. */
@Configuration(proxyBeanMethods = false)
public class WecomConnectorAutoConfiguration {
  @Bean
  WecomCallbackProtocol wecomCallbackProtocol() { return new WecomCallbackProtocol(); }
  @Bean
  WecomConnectionCredentialMapper wecomConnectionCredentialMapper() { return new WecomConnectionCredentialMapper(); }
  @Bean
  WecomEnterpriseAuthProfileProvider wecomEnterpriseAuthProfileProvider() { return new WecomEnterpriseAuthProfileProvider(); }
  @Bean
  WecomTokenCache wecomTokenCache() { return new WecomTokenCache(); }
  @Bean
  WecomAuthProviderBuilder wecomAuthProviderBuilder(@Qualifier("wecomAuthRestTemplate") RestTemplate http, WecomTokenCache tokens,
      @Value("${ouroboros.security.external-auth.wecom.http.overall-timeout-ms:15000}") long overallTimeoutMillis) {
    return new WecomAuthProviderBuilder(http, tokens, overallTimeoutMillis);
  }
  @Bean
  WecomApplicationTokens wecomApplicationTokens(
      @Qualifier("wecomAuthRestTemplate") RestTemplate http, WecomTokenCache tokens) {
    return new WecomApplicationTokens(http, tokens);
  }
  @Bean("wecomAuthRestTemplate")
  RestTemplate wecomAuthRestTemplate(
      org.springframework.beans.factory.ObjectProvider<com.ouroboros.enterprise.EnterpriseApiBudget> budgets,
      @Value("${ouroboros.security.external-auth.wecom.http.connect-timeout-ms:5000}") int connectTimeout,
      @Value("${ouroboros.security.external-auth.wecom.http.read-timeout-ms:10000}") int readTimeout) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.max(connectTimeout, 1)); factory.setReadTimeout(Math.max(readTimeout, 1));
    var http = new RestTemplate(factory);
    http.getInterceptors().add((request, body, execution) -> {
      budgets.getObject().acquire("wecom");
      return execution.execute(request, body);
    });
    return http;
  }
}
