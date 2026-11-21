package com.ouroboros.organizationstructure.directory.feishu;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;

@Configuration(proxyBeanMethods = false)
public class FeishuDirectoryAutoConfiguration {
  @Bean FeishuDirectorySource feishuDirectorySource(EnterpriseRpcClient client) { return new FeishuDirectorySource(client); }
}
