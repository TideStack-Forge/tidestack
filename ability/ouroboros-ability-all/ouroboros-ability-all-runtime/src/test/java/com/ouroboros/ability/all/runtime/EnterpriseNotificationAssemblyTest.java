package com.ouroboros.ability.all.runtime;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import com.ouroboros.enterprise.rpc.EnterpriseRpcClient;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionProviderRegistry;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.organizationstructure.directory.DirectoryMemberResolver;

class EnterpriseNotificationAssemblyTest {
  @Test
  void installedDescriptorsBindToEachContainersOwnServicesAndRemainUsableAfterAnotherContextCloses() {
    var runner = new ApplicationContextRunner().withUserConfiguration(
        com.ouroboros.notification.dingtalk.NotificationDingTalkAutoConfiguration.class,
        com.ouroboros.notification.wecom.WecomNotificationAutoConfiguration.class,
        com.ouroboros.notification.feishu.FeishuNotificationAutoConfiguration.class)
        .withBean(EnterpriseRpcClient.class, () -> mock(EnterpriseRpcClient.class))
        .withBean(EnterpriseConnectionCatalog.class, () -> mock(EnterpriseConnectionCatalog.class))
        .withBean(EnterpriseConnectionProviderRegistry.class, () -> mock(EnterpriseConnectionProviderRegistry.class))
        .withBean(DirectoryMemberResolver.class, () -> mock(DirectoryMemberResolver.class));
    runner.run(first -> {
      assertThat(first).hasNotFailed();
      var firstSuppliers = List.copyOf(first.getBeansOfType(NotificationAdapterSupplier.class).values());
      var firstWecom = NotificationAdapterCenter.findSupplier("wecom", firstSuppliers).orElseThrow().getAdapter();
      runner.run(second -> {
        assertThat(second).hasNotFailed();
        var secondSuppliers = List.copyOf(second.getBeansOfType(NotificationAdapterSupplier.class).values());
        for (String channel : List.of("ding-talk", "wecom", "feishu")) {
          var resolved = NotificationAdapterCenter.findSupplier(channel, secondSuppliers).orElseThrow();
          assertThat(resolved).isSameAs(secondSuppliers.stream().filter(supplier -> channel.equals(supplier.getName())).findFirst().orElseThrow());
          assertThat(resolved.getEmployeeRecipients()).isPresent();
          assertThat(resolved.getAdapter()).isNotSameAs(NotificationAdapterCenter.findSupplier(channel, firstSuppliers).orElseThrow().getAdapter());
          var descriptor = NotificationAdapterCenter.findSupplier(channel).orElseThrow();
          assertThat(descriptor.getChannelOptionsClass()).isEqualTo(resolved.getChannelOptionsClass());
          assertThat(descriptor.getChannelSchemaClass()).isEqualTo(resolved.getChannelSchemaClass());
        }
      });
      assertThat(NotificationAdapterCenter.findSupplier("wecom", firstSuppliers).orElseThrow().getAdapter()).isSameAs(firstWecom);
    });
  }
}
