package com.ouroboros.notification.enterprise.management;

import java.util.List;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import com.ouroboros.integration.management.*;

@Configuration(proxyBeanMethods = false)
@Import(EnterpriseNotificationManagementController.class)
public class EnterpriseNotificationManagementAutoConfiguration {
  @Bean IntegrationManagementContributor enterpriseNotificationIntegrationContributor() {
    return () -> List.of(new IntegrationManagementPage("enterprise-notifications", IntegrationManagementPage.Section.CONNECTIONS,
        "企业通知", "/system-setting/integration-management/enterprise-notifications", 50,
        Set.of(EnterpriseNotificationManagementController.VIEW)));
  }
}
