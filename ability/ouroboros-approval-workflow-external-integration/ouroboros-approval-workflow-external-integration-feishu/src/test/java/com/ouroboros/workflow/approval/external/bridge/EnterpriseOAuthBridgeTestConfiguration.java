package com.ouroboros.workflow.approval.external.bridge;

import com.ouroboros.enterprise.ResolvedEnterpriseConnection;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

import com.ouroboros.cache.CacheManager;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.security.externalauth.EnterpriseAuthProfileResolver;
import com.ouroboros.security.externalauth.ExternalAuthProviderFactory;
import com.ouroboros.security.externalauth.config.ExternalAuthConfiguration;
import com.ouroboros.security.externalauth.config.ExternalAuthProperties;
import com.ouroboros.security.externalauth.config.ExternalAuthProviderSpecSource;
import com.ouroboros.security.externalauth.secret.CompositeSecretResolver;
import com.ouroboros.security.externalauth.secret.SecretResolver;
import com.ouroboros.enterprise.feishu.FeishuEnterpriseAuthProfileProvider;
import com.ouroboros.enterprise.runtime.auth.EnterpriseConnectionAuthProfileResolver;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;

@TestConfiguration(proxyBeanMethods = false)
public class EnterpriseOAuthBridgeTestConfiguration {

  @Bean
  EnterpriseOAuthBridgeConnections enterpriseOAuthBridgeConnections() {
    return new EnterpriseOAuthBridgeConnections(List.of(
        connection("feishu-main", "feishu", "test:feishu-main"),
        connection("dingtalk-main", "dingtalk", "test:dingtalk-main"),
        connection("wecom-main", "wecom", "test:wecom-main")
    ));
  }

  @Bean
  ConfigurationService enterpriseConnectionConfigurationService(
      EnterpriseOAuthBridgeConnections connections) {
    ConfigurationService service = org.mockito.Mockito.mock(ConfigurationService.class);
    org.mockito.Mockito.when(service.getSnapshot(EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY))
        .thenAnswer(invocation -> Optional.of(new com.ouroboros.configuration.ConfigurationSnapshot(
            EnterpriseConnectionCatalog.CONNECTIONS_CONFIG_KEY, connections.current(), null,
            com.ouroboros.util.Json.toJsonString(connections.current()), null)));
    return service;
  }

  private static Map<String, Object> connection(
      String connectionId, String provider, String credentialRef) {
    return Map.of(
        "connectionId", connectionId,
        "provider", provider,
        "credentialRef", credentialRef);
  }

  static final class EnterpriseOAuthBridgeConnections {

    private final AtomicReference<List<Map<String, Object>>> current;

    EnterpriseOAuthBridgeConnections(List<Map<String, Object>> initialConnections) {
      this.current = new AtomicReference<>(List.copyOf(initialConnections));
    }

    List<Map<String, Object>> current() {
      return current.get();
    }

    void replace(List<Map<String, Object>> connections) {
      current.set(List.copyOf(connections));
    }
  }

  @Bean
  CompositeSecretResolver enterpriseCredentialSecretResolver() {
    return new CompositeSecretResolver(List.of(new SecretResolver() {
      @Override
      public boolean supports(String scheme) {
        return "test".equals(scheme);
      }

      @Override
      public Optional<String> resolve(String reference) {
        return Optional.of("{\"appId\":\"cli_context\",\"appSecret\":\"context-secret\"}");
      }
    }));
  }

  @Bean
  EnterpriseConnectionCatalog externalConnectionConfigProvider(
      ConfigurationService configurationService,
      CompositeSecretResolver secretResolver) {
    // 连接层只从 catalog 取 provider schema。本模块不依赖其它渠道模块，改用测试 schema：
    // 声明三个渠道真正会用到的连接字段，字段白名单行为与生产一致。
    return new EnterpriseConnectionCatalog(
        configurationService, Optional.of(secretResolver), List.of(),
        List.of(new BridgeSchemaProvider("feishu"), new BridgeSchemaProvider("dingtalk"),
            new BridgeSchemaProvider("wecom")));
  }

  /** 只提供 provider schema 的测试实现：字段集合覆盖三个渠道的连接字段。 */
  private static final class BridgeSchemaProvider
      implements com.ouroboros.enterprise.EnterpriseConnectionProvider {

    private final String provider;

    private BridgeSchemaProvider(String provider) {
      this.provider = provider;
    }

    @Override
    public String provider() {
      return provider;
    }

    @Override
    public java.util.Set<String> connectionConfigFields() {
      return java.util.Set.of("corpId", "departmentId");
    }

  }

  @Bean
  FeishuEnterpriseAuthProfileProvider feishuEnterpriseAuthProfileProvider() {
    return new FeishuEnterpriseAuthProfileProvider();
  }

  @Bean
  EnterpriseAuthProfileResolver enterpriseAuthProfileResolver(
      EnterpriseConnectionCatalog connectionConfigProvider,
      FeishuEnterpriseAuthProfileProvider profileProvider) {
    return new EnterpriseConnectionAuthProfileResolver(connectionConfigProvider, List.of(profileProvider), org.mockito.Mockito.mock(com.ouroboros.enterprise.EnterpriseApiBudget.class));
  }

  @Bean
  ExternalAuthProviderSpecSource externalAuthProviderSpecSource() {
    return List::of;
  }

  @Bean
  ExternalAuthConfiguration externalAuthConfiguration(ExternalAuthProperties properties) {
    return () -> properties;
  }

  @Bean
  ExternalAuthProperties externalAuthProperties() {
    ExternalAuthProperties properties = new ExternalAuthProperties();
    properties.setPublicBaseUrl("https://platform.example.com");
    return properties;
  }

  @Bean
  ExternalAuthConfiguration externalAuthConfiguration(ExternalAuthProperties properties) {
    return () -> properties;
  }

  @Bean
  CacheManager cacheManager() {
    return org.mockito.Mockito.mock(CacheManager.class);
  }

  @Bean
  ExternalAuthProviderFactory externalAuthProviderFactory() {
    return new ExternalAuthProviderFactory();
  }
}
