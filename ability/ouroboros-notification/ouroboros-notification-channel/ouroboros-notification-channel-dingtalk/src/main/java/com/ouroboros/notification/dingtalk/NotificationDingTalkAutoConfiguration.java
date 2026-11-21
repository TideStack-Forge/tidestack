package com.ouroboros.notification.dingtalk;

import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.runtime.*;
import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;
import com.ouroboros.notification.dingtalk.adapter.*;
import com.ouroboros.organizationstructure.directory.DirectoryMemberResolver;

@Configuration(proxyBeanMethods = false)
public class NotificationDingTalkAutoConfiguration {
  @Bean EnterpriseNotificationRecipients dingTalkNotificationRecipients(EnterpriseConnectionCatalog connections,
      EnterpriseConnectionProviderRegistry providers, DirectoryMemberResolver members) {
    return new EnterpriseNotificationRecipients("dingtalk", connections, providers, members);
  }
  @Bean DingTalkNotificationAdapter dingTalkNotificationAdapter(@Qualifier("dingTalkNotificationRecipients") EnterpriseNotificationRecipients recipients, EnterpriseRpcClient rpc) {
    return new DingTalkNotificationAdapter(recipients, rpc);
  }
  @Bean DingTalkNotificationAdapterSupplier dingTalkNotificationAdapterSupplier(DingTalkNotificationAdapter adapter,
      @Qualifier("dingTalkNotificationRecipients") EnterpriseNotificationRecipients recipients) {
    return new DingTalkNotificationAdapterSupplier(adapter, recipients);
  }
  @Bean RetiredDingtalkNotificationConfigurationPolicy retiredDingtalkNotificationConfigurationPolicy() {
    return new RetiredDingtalkNotificationConfigurationPolicy();
  }
}
