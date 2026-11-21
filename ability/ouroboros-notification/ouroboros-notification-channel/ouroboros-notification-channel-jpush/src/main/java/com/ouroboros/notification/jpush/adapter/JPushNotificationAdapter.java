package com.ouroboros.notification.jpush.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.notification.jpush.service.JPushService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Component
public class JPushNotificationAdapter
    implements NotificationAdapter<JPushNotificationChannelSchema, JPushNotificationChannelOptions> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  JPushService jPushService;

  @Override
  public void send(Collection<String> addresses, JPushNotificationChannelSchema channelSchema,
                   Map<String, Object> payload, JPushNotificationChannelOptions options) throws NotificationSendException {
    if (CollectionUtils.isEmpty(addresses)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
    }

    if (null == channelSchema) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_CHANNEL_EMPTY);
    } else {
      if (ObjectUtils.isEmpty(channelSchema.getTitleTemplate())) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "标题模版未定义");
      }

      if (ObjectUtils.isEmpty(channelSchema.getContentTemplate())) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "内容模版未定义");
      }
    }

    JPushNotificationChannelOptions channelOptions = ObjectUtils.isNotEmpty(options)
        ? options : new JPushNotificationChannelOptions();

    AtomicReference<String> title = new AtomicReference<>(channelSchema.getTitleTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(title)) {
      payload.forEach((key, value) -> title.set(title.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    AtomicReference<String> content = new AtomicReference<>(channelSchema.getContentTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(content)) {
      payload.forEach((key, value) -> content.set(content.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    try {
      jPushService.sendNotification(new ArrayList<>(addresses), title.get(), content.get(), channelOptions);
    } catch (Exception e) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR, e.getMessage());
    } finally {
      logger.debug("send registrationIds {}, channelSchema {}, payload {}, channelOptions {}",
          Json.toJsonString(addresses),
          Json.toJsonString(channelSchema),
          Json.toJsonString(payload),
          Json.toJsonString(channelOptions)
      );
    }
  }
}
