package com.ouroboros.notification.aliyun.sms.adapter;

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
import com.ouroboros.notification.aliyun.sms.adapter.constant.AliyunSmsConstant;

/**
 * @author liansz
 */
@Component
public class AliyunSmsNotificationAdapterSupplier {

  public AliyunSmsNotificationAdapterSupplier(AliyunSmsNotificationAdapter adapter) {
    Proxy.adapter = adapter;
  }

  public static class Proxy implements NotificationAdapterSupplier {

    static NotificationAdapter<? extends NotificationChannelSchema, ? extends NotificationChannelOptions> adapter;

    @Override
    public Optional<NotificationChannelManagement> getManagement() {
      return Optional.of(new NotificationChannelManagement("阿里云短信", List.of("notification.sms.aliyun"),
          Map.of("id", Field.required("供应商模板 ID")), Map.of("signName", Field.optional("短信签名"))));
    }

    @Override
    public String getName() {
      return AliyunSmsConstant.ADAPTER_NAME;
    }

    @Override
    public <D extends NotificationChannelSchema, O extends NotificationChannelOptions> NotificationAdapter<D, O> getAdapter() {
      return (NotificationAdapter<D, O>) adapter;
    }
  }
}
