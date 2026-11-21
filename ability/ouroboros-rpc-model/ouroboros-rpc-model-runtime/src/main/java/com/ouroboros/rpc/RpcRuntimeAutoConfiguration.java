package com.ouroboros.rpc;

import java.util.stream.Collectors;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;

import com.ouroboros.data.annotation.DataModelScan;
import com.ouroboros.lifecycle.event.OuroborosMetadataRefreshedEvent;
import com.ouroboros.rpc.execution.RpcInvocationExecutor;
import com.ouroboros.rpc.execution.RpcInvocationInterceptor;
import com.ouroboros.rpc.execution.RpcProtocolInvoker;
import com.ouroboros.rpc.execution.protocol.OpenApiRpcProtocolInvoker;
import com.ouroboros.rpc.security.RPCAuthCredentialCenter;
import com.ouroboros.rpc.util.HttpRpcRequestBuilder;

@ComponentScan
@DataModelScan
@Configuration
public class RpcRuntimeAutoConfiguration {
  final static Logger logger = LoggerFactory.getLogger(RpcRuntimeAutoConfiguration.class);

  @PostConstruct
  public void init() {
    logger.info("Ouroboros RPC Runtime Auto Configuration loaded");
  }

  @EventListener
  public void onApplicationEvent(OuroborosMetadataRefreshedEvent event) {
    RPCModelDefinitionCenter.refresh();
    RPCAuthCredentialCenter.refresh();
  }

  @EventListener
  public void onConfigurationUpdated(com.ouroboros.configuration.event.ConfigurationUpdateEvent<?> event) {
    // Structural credential choices (placement, signing preset, token request) follow configuration updates.
    RPCAuthCredentialCenter.refresh();
  }

  @Bean
  public OpenApiRpcProtocolInvoker openApiRpcProtocolInvoker(HttpRpcRequestBuilder httpRpcRequestBuilder) {
    return new OpenApiRpcProtocolInvoker(httpRpcRequestBuilder);
  }

  @Bean
  public RpcInvocationExecutor rpcInvocationExecutor(
      ObjectProvider<RpcProtocolInvoker> protocolInvokers,
      ObjectProvider<RpcInvocationInterceptor> interceptors,
      ObjectProvider<com.ouroboros.rpc.execution.RpcExecutionResourcesFactory> resourceFactories
  ) {
    return new RpcInvocationExecutor(
        protocolInvokers.orderedStream().collect(Collectors.toList()),
        interceptors.orderedStream().collect(Collectors.toList()),
        resourceFactories.orderedStream().toList()
    );
  }
}
