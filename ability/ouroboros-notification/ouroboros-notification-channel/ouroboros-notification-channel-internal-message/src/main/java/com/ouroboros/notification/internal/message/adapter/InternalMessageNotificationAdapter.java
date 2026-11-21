package com.ouroboros.notification.internal.message.adapter;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.notification.adapter.NotificationAdapter;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.notification.internal.message.constant.InternalMessagePlatformEnum;
import com.ouroboros.notification.internal.message.service.NotificationInternalMessageService;
import com.ouroboros.util.Json;

/**
 * @author liansz
 */
@Component
public class InternalMessageNotificationAdapter
    implements NotificationAdapter<InternalMessageNotificationChannelSchema, InternalMessageNotificationChannelOptions> {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  private NotificationInternalMessageService service;

  @Override
  @Transactional(rollbackFor = Exception.class)
  public void send(Collection<String> employeeIds, InternalMessageNotificationChannelSchema channelSchema,
                   Map<String, Object> payload, InternalMessageNotificationChannelOptions options) throws NotificationSendException {
    var prepared = prepare(employeeIds, channelSchema, payload, options);
    service.sendNotification(new ArrayList<>(employeeIds), prepared.title(), prepared.content(), prepared.options());
    service.notifyUnreadCount(employeeIds);
  }

  @Override
  public Optional<NotificationChannelResult> sendWithResult(String deliveryKey,
      Collection<String> employeeIds, InternalMessageNotificationChannelSchema channelSchema, Map<String, Object> payload,
      InternalMessageNotificationChannelOptions options) {
    Prepared prepared;
    try { prepared = prepare(employeeIds, channelSchema, payload, options); }
    catch (NotificationSendException invalid) {
      return Optional.of(new NotificationChannelResult(
          NotificationChannelResult.Outcome.BLOCKED, "internal-message-configuration-invalid:" + invalid.getErrorCode()));
    }
    return Optional.of(service.sendNotificationWithResult(deliveryKey, new ArrayList<>(employeeIds),
        prepared.title(), prepared.content(), prepared.options()));
  }

  private Prepared prepare(Collection<String> employeeIds, InternalMessageNotificationChannelSchema channelSchema,
      Map<String, Object> payload, InternalMessageNotificationChannelOptions options) {
    if (CollectionUtils.isEmpty(employeeIds)) {
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

    InternalMessageNotificationChannelOptions channelOptions = ObjectUtils.isNotEmpty(options)
        ? options : new InternalMessageNotificationChannelOptions();

    Map<String, Boolean> platformMap = channelOptions.getPlatform();
    if (ObjectUtils.isNotEmpty(platformMap)) {
      for (String platform : platformMap.keySet()) {
        InternalMessagePlatformEnum.valueOfEnum(platform).orElseThrow(
            () -> new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_OPTIONS_ERROR, "接收平台错误")
        );
      }
    } else {
      channelOptions.setPlatform(
          Arrays.stream(InternalMessagePlatformEnum.values())
              .collect(Collectors.toMap(
                  InternalMessagePlatformEnum::getValue,
                  v -> true,
                  (v1, v2) -> v2
              ))
      );
    }

    logger.debug("send employeeIds {}, channelSchema {}, payload {}, channelOptions {}",
        Json.toJsonString(employeeIds),
        Json.toJsonString(channelSchema),
        Json.toJsonString(payload),
        Json.toJsonString(channelOptions)
    );

    AtomicReference<String> title = new AtomicReference<>(channelSchema.getTitleTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(title)) {
      payload.forEach((key, value) -> title.set(title.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    AtomicReference<String> content = new AtomicReference<>(channelSchema.getContentTemplate());
    if (payload != null && ObjectUtils.isNotEmpty(content)) {
      payload.forEach((key, value) -> content.set(content.get().replace(String.format("${%s}", key), String.valueOf(value))));
    }

    return new Prepared(title.get(), content.get(), channelOptions);
  }

  private record Prepared(String title, String content, InternalMessageNotificationChannelOptions options) { }
}
