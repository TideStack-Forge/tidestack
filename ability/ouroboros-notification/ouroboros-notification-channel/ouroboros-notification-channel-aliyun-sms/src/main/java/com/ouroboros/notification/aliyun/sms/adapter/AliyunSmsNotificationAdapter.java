package com.ouroboros.notification.aliyun.sms.adapter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.aliyun.sms.service.AliyunSmsService;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
@Component
public class AliyunSmsNotificationAdapter
    implements NotificationAdapter<AliyunSmsNotificationChannelSchema, AliyunSmsNotificationChannelOptions> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Autowired
  AliyunSmsService aliyunSmsService;

  @Override
  public void send(Collection<String> addresses, AliyunSmsNotificationChannelSchema channelSchema,
                   Map<String, Object> payload, AliyunSmsNotificationChannelOptions options) throws NotificationSendException {
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

    AliyunSmsNotificationChannelOptions channelOptions = options != null
        // TODO: 审查tryToBean错误处理逻辑
        ? Maps.tryToBean(AliyunSmsNotificationChannelOptions.class, Maps.fromBean(options)).getOrElse(AliyunSmsNotificationChannelOptions::new)
        : new AliyunSmsNotificationChannelOptions();

    configurationService.get(String.class, "notification.aliyun.sms.signName")
        .ifPresent(signName ->
            channelOptions.setSignName(
                ObjectUtils.isNotEmpty(channelOptions.getSignName())
                    ? channelOptions.getSignName()
                    : signName
            )
        );

    try {
      aliyunSmsService.sendSms(new ArrayList<>(addresses), channelSchema.getId(), payload, channelOptions);
    } catch (Exception e) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.SEND_RESPONSE_ERROR, e.getMessage());
    } finally {
      if (!(payload instanceof com.ouroboros.notification.SensitiveNotificationPayload)) { logger.debug("send mobiles {}, channelSchema {}, payload {}, channelOptions {}",
          Json.toJsonString(addresses),
          Json.toJsonString(channelSchema),
          Json.toJsonString(payload),
          Json.toJsonString(channelOptions)
      );
      }
    }
  }
}
