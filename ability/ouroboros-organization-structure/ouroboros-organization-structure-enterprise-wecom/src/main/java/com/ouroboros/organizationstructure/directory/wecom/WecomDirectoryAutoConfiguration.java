package com.ouroboros.organizationstructure.directory.wecom;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;

@Configuration(proxyBeanMethods = false)
public class WecomDirectoryAutoConfiguration {
  @Bean WecomDirectorySource wecomDirectorySource(EnterpriseRpcClient client) { return new WecomDirectorySource(client); }
}
