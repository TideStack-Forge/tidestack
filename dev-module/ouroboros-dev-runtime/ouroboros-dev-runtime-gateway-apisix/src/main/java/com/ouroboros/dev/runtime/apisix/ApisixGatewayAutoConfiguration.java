package com.ouroboros.dev.runtime.apisix;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ouroboros.dev.runtime.apisix.client.ApisixAdminClient;
import com.ouroboros.dev.runtime.apisix.client.RestTemplateApisixAdminClient;
import com.ouroboros.dev.runtime.apisix.command.ApisixGatewayRouteSyncConsumer;
import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.mapper.ApisixRouteMapper;
import com.ouroboros.dev.runtime.config.DevRuntimeProperties;

@Configuration
@ConditionalOnProperty(prefix = "ouroboros.dev.runtime.gateway", name = "enabled", havingValue = "true")
@ConditionalOnExpression("'${ouroboros.dev.runtime.gateway.provider:}' == 'apisix'")
public class ApisixGatewayAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean(ApisixGatewayProperties.class)
  @ConditionalOnProperty(prefix = "ouroboros.dev.runtime.gateway.apisix", name = {"base-url", "admin-key", "route-id-prefix"})
  public ApisixGatewayProperties apisixGatewayProperties(DevRuntimeProperties properties) {
    DevRuntimeProperties.ApisixProperties source = properties.getGateway().getApisix();
    ApisixGatewayProperties target = new ApisixGatewayProperties();
    target.setBaseUrl(source.getBaseUrl());
    target.setAdminKey(source.getAdminKey());
    target.setRouteIdPrefix(source.getRouteIdPrefix());
    target.setConnectTimeoutMillis(source.getConnectTimeoutMillis());
    target.setReadTimeoutMillis(source.getReadTimeoutMillis());
    return target;
  }

  @Bean
  @ConditionalOnBean(ApisixGatewayProperties.class)
  @ConditionalOnMissingBean(ApisixAdminClient.class)
  public ApisixAdminClient apisixAdminClient(ApisixGatewayProperties properties) {
    return new RestTemplateApisixAdminClient(properties);
  }

  @Bean
  @ConditionalOnBean(ApisixGatewayProperties.class)
  @ConditionalOnMissingBean(ApisixRouteMapper.class)
  public ApisixRouteMapper apisixRouteMapper(ApisixGatewayProperties properties) {
    return new ApisixRouteMapper(properties);
  }

  @Bean
  @ConditionalOnBean({ApisixAdminClient.class, ApisixRouteMapper.class})
  @ConditionalOnMissingBean(ApisixGatewayRouteSyncConsumer.class)
  public ApisixGatewayRouteSyncConsumer apisixGatewayRouteSyncConsumer(
      ApisixAdminClient apisixAdminClient,
      ApisixRouteMapper apisixRouteMapper
  ) {
    return new ApisixGatewayRouteSyncConsumer(apisixAdminClient, apisixRouteMapper);
  }
}
