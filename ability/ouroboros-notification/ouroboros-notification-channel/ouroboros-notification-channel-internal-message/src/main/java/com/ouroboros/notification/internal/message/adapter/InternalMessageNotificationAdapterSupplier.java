package com.ouroboros.notification.internal.message.adapter;

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
import com.ouroboros.notification.internal.message.adapter.constant.InternalMessageConstant;

/**
 * @author liansz
 */
@Component
public class InternalMessageNotificationAdapterSupplier {

  public InternalMessageNotificationAdapterSupplier(InternalMessageNotificationAdapter adapter) {
    Proxy.adapter = adapter;
  }

  public static class Proxy implements NotificationAdapterSupplier {

    static NotificationAdapter<? extends NotificationChannelSchema, ? extends NotificationChannelOptions> adapter;

    @Override
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("站内消息", List.of("notification.internalMessage"),
          Map.of("titleTemplate", Field.required("标题模板"), "contentTemplate", Field.required("正文模板")), Map.of()));
    }

    @Override
    public String getName() {
      return InternalMessageConstant.ADAPTER_NAME;
    }

    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) adapter;
    }
  }
}
