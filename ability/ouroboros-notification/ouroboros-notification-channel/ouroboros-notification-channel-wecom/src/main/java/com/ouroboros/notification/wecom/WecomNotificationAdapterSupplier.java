package com.ouroboros.notification.wecom;

import java.util.*;
import com.ouroboros.notification.adapter.*;
import com.ouroboros.notification.adapter.NotificationChannelManagement.Field;
import com.ouroboros.notification.employee.NotificationChannelRecipients;
import com.ouroboros.notification.enterprise.EnterpriseNotificationRecipients;

public final class WecomNotificationAdapterSupplier implements NotificationAdapterSupplier {
  private final WecomNotificationAdapter adapter;
  private final EnterpriseNotificationRecipients recipients;
  public WecomNotificationAdapterSupplier(WecomNotificationAdapter adapter, EnterpriseNotificationRecipients recipients) {
    this.adapter = adapter; this.recipients = recipients;
  }

  @Override public String getName() { return "wecom"; }
  @Override public Optional<NotificationChannelRecipients> getEmployeeRecipients() { return Optional.of(recipients); }
  @Override public Optional<NotificationChannelManagement> getManagement() { return new Proxy().getManagement(); }
  @Override public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
    return (NotificationAdapter<D, O>) adapter;
  }

  public static final class Proxy implements NotificationAdapterSupplier {
    @Override public Optional<Class<? extends NotificationAdapterSupplier>> managedSupplierType() { return Optional.of(WecomNotificationAdapterSupplier.class); }
    @Override public Class<? extends NotificationChannelSchema> getChannelSchemaClass() { return com.ouroboros.notification.enterprise.EnterpriseNotificationChannelSchema.class; }
    @Override public Class<? extends NotificationChannelOptions> getChannelOptionsClass() { return com.ouroboros.notification.enterprise.EnterpriseNotificationChannelOptions.class; }
    @Override public String getName() { return "wecom"; }
    @Override public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("企业微信通知", List.of(),
          Map.of("titleTemplate", Field.required("标题模板"), "contentTemplate", Field.required("正文模板"),
              "linkTemplate", Field.optional("业务链接模板")), Map.of()));
    }
    @Override public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      throw new com.ouroboros.exception.ValidationException("通知渠道需要通过当前容器装配");
    }
  }
}
