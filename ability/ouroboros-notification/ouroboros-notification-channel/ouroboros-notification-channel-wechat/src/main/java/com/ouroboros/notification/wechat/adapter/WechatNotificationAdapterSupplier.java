package com.ouroboros.notification.wechat.adapter;

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
import com.ouroboros.notification.wechat.adapter.constant.WechatConstant;

/**
 * @author liansz
 */
@Component
public class WechatNotificationAdapterSupplier {

  public WechatNotificationAdapterSupplier(WechatNotificationAdapter adapter) {
    Proxy.adapter = adapter;
  }

  public static class Proxy implements NotificationAdapterSupplier {

    static NotificationAdapter<? extends NotificationChannelSchema, ? extends NotificationChannelOptions> adapter;

    @Override
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("微信通知", List.of("notification.wechat"),
          Map.of("id", Field.required("供应商模板 ID")), Map.of()));
    }

    @Override
    public String getName() {
      return WechatConstant.ADAPTER_NAME;
    }

    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) adapter;
    }
  }
}
