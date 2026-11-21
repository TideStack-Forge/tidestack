package com.ouroboros.notification.dingtalk.adapter;

import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;
import com.ouroboros.notification.employee.NotificationChannelRecipients;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.notification.adapter.NotificationChannelManagement;
import com.ouroboros.notification.adapter.NotificationChannelManagement.Field;

import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.dingtalk.adapter.constant.DingTalkConstant;

/**
 * @author liansz
 */
public class DingTalkNotificationAdapterSupplier implements NotificationAdapterSupplier {
  private final DingTalkNotificationAdapter adapter;
  private final EnterpriseNotificationRecipients recipients;

  public DingTalkNotificationAdapterSupplier(DingTalkNotificationAdapter adapter, EnterpriseNotificationRecipients recipients) {
    this.adapter = adapter; this.recipients = recipients;
  }

  @Override public String getName() { return DingTalkConstant.ADAPTER_NAME; }
  @Override public Optional<NotificationChannelRecipients> getEmployeeRecipients() { return Optional.of(recipients); }
  @Override public Optional<NotificationChannelManagement> getManagement() { return new Proxy().getManagement(); }
  @Override public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
    return (NotificationAdapter<D, O>) adapter;
  }

  public static class Proxy implements NotificationAdapterSupplier {

    @Override public Optional<Class<? extends NotificationAdapterSupplier>> managedSupplierType() { return Optional.of(DingTalkNotificationAdapterSupplier.class); }
    @Override public Class<? extends NotificationChannelSchema> getChannelSchemaClass() { return DingTalkNotificationChannelSchema.class; }
    @Override public Class<? extends NotificationChannelOptions> getChannelOptionsClass() { return DingTalkNotificationChannelOptions.class; }

    @Override
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("钉钉通知", List.of(),
          Map.of("titleTemplate", Field.required("标题模板"), "contentTemplate", Field.required("正文模板"), "linkTemplate", Field.optional("业务链接模板")),
          Map.of("agentId", Field.optional("旧 AgentID（仅校验，应用由企业连接决定）"),
              "appSkipUrl", Field.optional("移动端链接"), "pcSkipUrl", Field.optional("PC 链接"),
              "author", Field.optional("发送方"), "statusContent", Field.optional("状态提示"))));
    }

    @Override
    public String getName() {
      return DingTalkConstant.ADAPTER_NAME;
    }

    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      throw new com.ouroboros.exception.ValidationException("通知渠道需要通过当前容器装配");
    }
  }
}
