package com.ouroboros.organizationstructure.directory.dingtalk;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;

@Configuration(proxyBeanMethods = false)
public class DingtalkDirectoryAutoConfiguration {
  @Bean DingtalkDirectorySource dingtalkDirectorySource(EnterpriseRpcClient client) { return new DingtalkDirectorySource(client); }
}
