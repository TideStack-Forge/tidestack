package com.ouroboros.integration.management;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(IntegrationManagementController.class)
public class IntegrationManagementAutoConfiguration {
  @Bean
  public IntegrationWorkspace integrationWorkspace(List<IntegrationManagementContributor> contributors) {
    return new IntegrationWorkspace(contributors);
  }
}
