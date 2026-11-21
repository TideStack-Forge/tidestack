package com.ouroboros.notification.jpush.adapter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.notification.adapter.NotificationChannelManagement;
import com.ouroboros.notification.adapter.NotificationChannelManagement.Field;

import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.adapter.NotificationAdapterSupplier;
import com.ouroboros.notification.adapter.NotificationChannelOptions;
import com.ouroboros.notification.adapter.NotificationChannelSchema;
import com.ouroboros.notification.jpush.adapter.constant.JPushConstant;

/**
 * @author liansz
 */
@Component
public class JPushNotificationAdapterSupplier {

  public JPushNotificationAdapterSupplier(JPushNotificationAdapter adapter) {
    Proxy.adapter = adapter;
  }

  public static class Proxy implements NotificationAdapterSupplier {

    static NotificationAdapter<? extends NotificationChannelSchema, ? extends NotificationChannelOptions> adapter;

    @Override
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("极光通知", List.of("notification.jpush"),
          Map.of("titleTemplate", Field.required("标题模板"), "contentTemplate", Field.required("正文模板")), Map.of()));
    }

    @Override
    public String getName() {
      return JPushConstant.ADAPTER_NAME;
    }

    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) adapter;
    }
  }
}
