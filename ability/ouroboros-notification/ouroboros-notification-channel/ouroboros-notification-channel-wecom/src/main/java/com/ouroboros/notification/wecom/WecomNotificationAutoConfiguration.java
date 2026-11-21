package com.ouroboros.notification.wecom;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;
import com.ouroboros.organizationstructure.directory.DirectoryMemberResolver;

@Configuration(proxyBeanMethods = false)
public class WecomNotificationAutoConfiguration {
  @Bean EnterpriseNotificationRecipients wecomNotificationRecipients(EnterpriseConnectionCatalog connections,
      EnterpriseConnectionProviderRegistry providers, DirectoryMemberResolver members) {
    return new EnterpriseNotificationRecipients("wecom", connections, providers, members);
  }
  @Bean WecomNotificationAdapter wecomNotificationAdapter(@Qualifier("wecomNotificationRecipients") EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    return new WecomNotificationAdapter(recipients, rpc);
  }
  @Bean WecomNotificationAdapterSupplier wecomNotificationAdapterSupplier(WecomNotificationAdapter adapter,
      @Qualifier("wecomNotificationRecipients") EnterpriseNotificationRecipients recipients) {
    return new WecomNotificationAdapterSupplier(adapter, recipients);
  }
}
