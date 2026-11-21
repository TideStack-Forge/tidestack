package com.ouroboros.notification.wechat.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.notification.wechat.service.WechatService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Component
public class WechatNotificationAdapter
    implements NotificationAdapter<WechatNotificationChannelSchema, WechatNotificationChannelOptions> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  WechatService wechatService;

  @Override
  public void send(Collection<String> addresses, WechatNotificationChannelSchema channelSchema,
                   Map<String, Object> payload, WechatNotificationChannelOptions options) throws NotificationSendException {
    if (CollectionUtils.isEmpty(addresses)) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.ADDRESSES_EMPTY);
    }

    if (null == channelSchema) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.TEMPLATE_CHANNEL_EMPTY);
    } else {
      if (ObjectUtils.isEmpty(channelSchema.getId())) {
        throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "模版 ID 未定义");
      }
    }

    WechatNotificationChannelOptions channelOptions = ObjectUtils.isNotEmpty(options)
        ? options : new WechatNotificationChannelOptions();

    logger.debug("send wechatIds {}, channelSchema {}, payload {}, channelOptions {}",
        Json.toJsonString(addresses),
        Json.toJsonString(channelSchema),
        Json.toJsonString(payload),
        Json.toJsonString(channelOptions)
    );

    Map<String, Exception> exceptionMap = new HashMap<>();
    for (String wechatId : addresses) {
      try {
        wechatService.sendTemplateMessage(wechatId, channelSchema.getId(), payload, channelOptions);
      } catch (Exception e) {
        exceptionMap.put(wechatId, e);
      }
    }

    if (!CollectionUtils.isEmpty(exceptionMap)) {
      String[] exceptionMessages = exceptionMap.values().stream()
          .map(Throwable::getMessage)
          .collect(Collectors.toList())
          .toArray(new String[0]);

      throw new NotificationSendException(NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR, exceptionMessages);
    }
  }
}
