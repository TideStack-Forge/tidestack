package com.ouroboros.notification.feishu;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;
import com.ouroboros.organizationstructure.directory.DirectoryMemberResolver;

@Configuration(proxyBeanMethods = false)
public class FeishuNotificationAutoConfiguration {
  @Bean EnterpriseNotificationRecipients feishuNotificationRecipients(EnterpriseConnectionCatalog connections,
      EnterpriseConnectionProviderRegistry providers, DirectoryMemberResolver members) {
    return new EnterpriseNotificationRecipients("feishu", connections, providers, members);
  }
  @Bean FeishuNotificationAdapter feishuNotificationAdapter(@Qualifier("feishuNotificationRecipients") EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    return new FeishuNotificationAdapter(recipients, rpc);
  }
  @Bean FeishuNotificationAdapterSupplier feishuNotificationAdapterSupplier(FeishuNotificationAdapter adapter,
      @Qualifier("feishuNotificationRecipients") EnterpriseNotificationRecipients recipients) {
    return new FeishuNotificationAdapterSupplier(adapter, recipients);
  }
}
