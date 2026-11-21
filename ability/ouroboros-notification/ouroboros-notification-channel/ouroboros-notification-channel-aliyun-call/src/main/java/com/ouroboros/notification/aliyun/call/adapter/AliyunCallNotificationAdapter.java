package com.ouroboros.notification.aliyun.call.adapter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.aliyun.call.service.AliyunCallService;
import com.ouroboros.notification.aliyun.call.service.model.AliyunCallResult;
import com.ouroboros.notification.call.adapter.AbstractCallNotificationAdapter;
import com.ouroboros.notification.call.adapter.CallStateQueryResult;
import com.ouroboros.notification.call.enums.CallState;
import com.ouroboros.notification.exception.NotificationSendException;
import com.ouroboros.util.Json;
import com.ouroboros.util.Maps;

/**
 * @author liansz
 */
@Component
public class AliyunCallNotificationAdapter
    extends AbstractCallNotificationAdapter<AliyunCallNotificationChannelSchema, AliyunCallNotificationChannelOptions> {

  private static final long DEFAULT_PROD_ID = 11000000300006L;

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private static final Set<String> CONNECTED_STATES = new HashSet<>(Arrays.asList("200000", "200001", "SUCCESS"));

  private static final Set<String> UNKNOWN_STATES = new HashSet<>(Arrays.asList("200100", "200101", "200102", "200103"));

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Autowired
  AliyunCallService aliyunCallService;

  @Override
  protected void validateChannelSchema(AliyunCallNotificationChannelSchema channelSchema) {
    super.validateChannelSchema(channelSchema);
    if (ObjectUtils.isEmpty(channelSchema.getId())) {
      throw new NotificationSendException(NotificationSendException.ErrorDefine.CHANNEL_SCHEMA_ERROR, "模版 ID 未定义");
    }
  }

  @Override
  protected Map<String, Object> sendAndCollect(String address, AliyunCallNotificationChannelSchema channelSchema,
                                               Map<String, Object> payload,
                                               AliyunCallNotificationChannelOptions options) {
    AliyunCallNotificationChannelOptions channelOptions = options != null
        // TODO: 审查tryToBean错误处理逻辑
        ? Maps.tryToBean(AliyunCallNotificationChannelOptions.class, Maps.fromBean(options)).getOrElse(AliyunCallNotificationChannelOptions::new)
        : new AliyunCallNotificationChannelOptions();

    configurationService.get(String.class, "notification.aliyun.call.calledShowNumber")
        .ifPresent(calledShowNumber ->
            channelOptions.setCalledShowNumber(
                ObjectUtils.isNotEmpty(channelOptions.getCalledShowNumber())
                    ? channelOptions.getCalledShowNumber()
                    : calledShowNumber
            )
        );

    configurationService.get(Integer.class, "notification.aliyun.call.playTimes")
        .ifPresent(playTimes ->
            channelOptions.setPlayTimes(
                ObjectUtils.isNotEmpty(channelOptions.getPlayTimes())
                    ? channelOptions.getPlayTimes()
                    : playTimes
            )
        );

    configurationService.get(Integer.class, "notification.aliyun.call.volume")
        .ifPresent(volume ->
            channelOptions.setVolume(
                ObjectUtils.isNotEmpty(channelOptions.getVolume())
                    ? channelOptions.getVolume()
                    : volume
            )
        );

    configurationService.get(Integer.class, "notification.aliyun.call.speed")
        .ifPresent(speed ->
            channelOptions.setSpeed(
                ObjectUtils.isNotEmpty(channelOptions.getSpeed())
                    ? channelOptions.getSpeed()
                    : speed
            )
        );

    if (ObjectUtils.isEmpty(channelOptions.getProdId())) {
      channelOptions.setProdId(String.valueOf(DEFAULT_PROD_ID));
    }

    logger.debug("send mobiles {}, channelSchema {}, payload {}, channelOptions {}",
        address, Json.toJsonString(channelSchema), Json.toJsonString(payload), Json.toJsonString(channelOptions));

    String callId = aliyunCallService.sendCall(address, channelSchema.getId(), payload, channelOptions);

    Map<String, Object> result = new HashMap<>();
    result.put("callId", callId);
    result.put("prodId", Long.parseLong(channelOptions.getProdId()));
    result.put("queryDate", System.currentTimeMillis());

    return result;
  }

  @Override
  public CallStateQueryResult query(Map<String, Object> receipt) {
    AliyunCallResult result = aliyunCallService.queryState(
        (String) receipt.get("callId"),
        Maps.getAs(receipt, "prodId", Number.class).map(Number::longValue).orElse(11000000300006L),
        Maps.getAs(receipt, "queryDate", Number.class).map(Number::longValue).orElse(System.currentTimeMillis())
    );

    CallStateQueryResult queryResult = new CallStateQueryResult();
    queryResult.setState(resolveState(result));
    queryResult.setStartTime(parseDateTime(result != null ? result.getStartDate() : null));
    queryResult.setEndTime(parseDateTime(result != null ? result.getEndDate() : null));
    queryResult.setDuration(result != null ? result.getDuration() : null);
    return queryResult;
  }

  private static CallState resolveState(AliyunCallResult result) {
    if (result == null) {
      return CallState.UNKNOWN;
    }

    String state = result.getState();
    Long duration = result.getDuration();
    if ((state != null && CONNECTED_STATES.contains(state.toUpperCase())) || (duration != null && duration > 0)) {
      return CallState.CONNECTED;
    }
    if (state == null || UNKNOWN_STATES.contains(state)) {
      return CallState.UNKNOWN;
    }
    if (ObjectUtils.isNotEmpty(state)) {
      return CallState.NOT_CONNECTED;
    }
    return CallState.UNKNOWN;
  }

  private static LocalDateTime parseDateTime(String value) {
    if (ObjectUtils.isEmpty(value)) {
      return null;
    }
    return LocalDateTime.parse(value, DATE_TIME_FORMATTER);
  }
}
