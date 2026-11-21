package com.ouroboros.notification.email.adapter;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.email.service.EmailService;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
@Component
public class EmailNotificationAdapter
    implements NotificationAdapter<EmailNotificationChannelSchema, EmailNotificationChannelOptions> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Autowired
  EmailService emailService;

  @Override
  public void send(Collection<String> addresses, EmailNotificationChannelSchema channelSchema,
                   Map<String, Object> payload, EmailNotificationChannelOptions options) throws NotificationSendException {
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

    EmailNotificationChannelOptions channelOptions = options != null
        // TODO: 审查 tryToBean 错误处理
        ? Maps.tryToBean(EmailNotificationChannelOptions.class, Maps.fromBean(options)).getOrElse(EmailNotificationChannelOptions::new)
        : new EmailNotificationChannelOptions();

    AtomicReference<String> title = new AtomicReference<>(channelSchema.getTitleTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(title)) {
      payload.forEach((key, value) -> title.set(title.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    AtomicReference<String> content = new AtomicReference<>(channelSchema.getContentTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(content)) {
      payload.forEach((key, value) -> content.set(content.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    if (!(payload instanceof com.ouroboros.notification.SensitiveNotificationPayload)) { logger.debug("send emails {}, channelSchema {}, payload {}, channelOptions {}",
        Json.toJsonString(addresses),
        Json.toJsonString(channelSchema),
        Json.toJsonString(payload),
        Json.toJsonString(channelOptions)
    );

    }

    // 发送数据
    Map<String, Exception> exceptionMap = new HashMap<>();
    for (String email : addresses) {
      try {
        emailService.sendEmail(email, title.get(), content.get(), channelOptions);
      } catch (Exception e) {
        exceptionMap.put(email, e);
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
