package com.ouroboros.enterprise.rpc;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.rpc.execution.*;
import com.ouroboros.rpc.RPCModelDefinitionCenter;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.util.RpcConfigurationTemplates;

@Configuration(proxyBeanMethods = false)
public class EnterpriseRpcAutoConfiguration {
  public static final String USE = "systemSetting.integrationManagement.connections.rpc.invoke";
  public static final String CATALOG = "systemSetting.integrationManagement.connections.rpc.catalog";

  @Bean
  com.ouroboros.integration.management.IntegrationManagementContributor enterpriseRpcIntegrationContributor() {
    return () -> List.of(new com.ouroboros.integration.management.IntegrationManagementPage("enterprise-rpc",
        com.ouroboros.integration.management.IntegrationManagementPage.Section.CONNECTIONS,
        "企业开放 API", "/system-setting/integration-management/enterprise-rpc", 40,
        java.util.Set.of(CATALOG)));
  }

  @Bean
  EnterpriseRpcClient enterpriseRpcClient(RpcInvocationExecutor executor, EnterpriseConnectionCatalog connections,
      com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry providers, List<EnterpriseApplicationTokens> tokens,
      ConfigurationService configuration, Environment environment, com.ouroboros.enterprise.EnterpriseApiBudget apiBudget) {
    return new EnterpriseRpcClient(executor, connections, providers, tokens,
        key -> configuration.get(key).map(Object::toString).orElseGet(() -> environment.getProperty(key)), apiBudget);
  }

  @Bean
  RpcExecutionResourcesFactory enterpriseRpcResourcesFactory(EnterpriseConnectionCatalog connections,
      com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry providers,
      List<EnterpriseApplicationTokens> tokenServices, ConfigurationService configuration, Environment environment,
      ObjectProvider<RpcInvocationExecutor> executor, com.ouroboros.enterprise.EnterpriseApiBudget apiBudget) {
    Map<String, EnterpriseApplicationTokens> tokens = tokenServices.stream().collect(Collectors.toUnmodifiableMap(
        EnterpriseApplicationTokens::provider, Function.identity()));
    Function<String, String> config = key -> configuration.get(key).map(Object::toString).orElseGet(() -> environment.getProperty(key));
    return (request, parent) -> {
      if (parent instanceof EnterpriseRpcResources) { return parent; }
      var definition = parent == null ? RPCModelDefinitionCenter.getDefinition(request.getModelName()).orElse(null)
          : parent.findDefinition(request.getModelName()).orElse(null);
      if (parent == null) {
        if (definition == null || definition.getAuthCredential() == null) { return null; }
        String name = RpcConfigurationTemplates.resolve(definition.getAuthCredential(), config);
        if (!(RPCAuthCredentialCenter.getRpcCredential(name).orElse(null) instanceof EnterpriseAccessTokenCredential)) {
          return null;
        }
      }
      return new EnterpriseRpcResources(parent, connections, providers, tokens,
          provider -> EnterpriseConnectionAccess.require(USE), config,
          (nested, resources) -> executor.getObject().invoke(nested, resources), budget(request, definition, config), apiBudget);
    };
  }

  private long budget(RpcInvocationRequest request, com.ouroboros.rpc.RPCModelDefinition definition,
      Function<String, String> config) {
    Object value = request.getOptions().get("timeoutOverrideMs");
    if (value == null && definition != null) { value = definition.getExtraProps().get("timeout"); }
    long duration = value == null ? 3000L : Long.parseLong(RpcConfigurationTemplates.resolve(value.toString(), config));
    if (duration < 1) { throw new com.ouroboros.exception.ValidationException("RPC 超时必须为正数"); }
    return Math.min(60000L, duration);
  }
}
