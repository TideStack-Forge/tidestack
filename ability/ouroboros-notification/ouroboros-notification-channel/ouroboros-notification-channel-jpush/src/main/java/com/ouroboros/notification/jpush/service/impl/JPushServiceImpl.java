package com.ouroboros.notification.jpush.service.impl;

import java.lang.reflect.Field;
import java.util.List;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.jpush.adapter.JPushNotificationChannelOptions;
import com.ouroboros.notification.jpush.service.JPushService;
import com.ouroboros.util.Json;

import cn.jiguang.common.ClientConfig;
import cn.jpush.api.JPushClient;
import cn.jpush.api.push.PushResult;
import cn.jpush.api.push.model.Platform;
import cn.jpush.api.push.model.PushPayload;
import cn.jpush.api.push.model.audience.Audience;
import cn.jpush.api.push.model.audience.AudienceTarget;
import cn.jpush.api.push.model.notification.AndroidNotification;
import cn.jpush.api.push.model.notification.IosNotification;
import cn.jpush.api.push.model.notification.Notification;

/**
 * @author liansz
 */
@Service
public class JPushServiceImpl implements JPushService {

  private final Logger logger = LoggerFactory.getLogger(getClass());

  @Autowired
  ConfigurationService configurationService;

  @Override
  public void sendNotification(List<String> registrationIds, String title, String content,
                               JPushNotificationChannelOptions channelOptions) {
    PushPayload pushPayload = buildRequest(registrationIds, title, content, channelOptions);
    PushResult pushResult = null;
    RuntimeException exception = null;
    try {
      pushResult = buildJPushClient().sendPush(pushPayload);
      if (!pushResult.isResultOK()) {
        throw new BusinessException("极光推送失败：" + pushResult.error.getMessage());
      }
    } catch (Exception e) {
      exception = new RuntimeException(e);
      throw exception;
    } finally {
      logger.debug("send request {}, response {}",
          Json.toJsonString(pushPayload),
          exception != null ? exception.getMessage() : Json.toJsonString(pushResult)
      );
    }
  }

  /**
   * 生成极光推送对象PushPayload
   *
   * @param registrationIds
   * @param title
   * @param contents
   * @return PushPayload
   */
  private PushPayload buildRequest(List<String> registrationIds, String title, String contents,
                                   JPushNotificationChannelOptions channelOptions) {
    JPushNotificationChannelOptions.NotificationOptions notification = channelOptions.getNotification() != null
        ? channelOptions.getNotification() : new JPushNotificationChannelOptions.NotificationOptions();

    AndroidNotification.Builder androidNotificationBuilder = AndroidNotification.newBuilder()
        .addExtra("type", "infomation")
        .setTitle(title)
        .setAlert(contents)
        .setBigText(contents);
    copyObjectValue(notification.getAndroid(), androidNotificationBuilder);

    IosNotification.Builder iosNotificationBuilder = IosNotification.newBuilder()
        .addExtra("type", "infomation")
        .setAlert(contents);
    copyObjectValue(notification.getIos(), iosNotificationBuilder);

    return PushPayload.newBuilder()
        .setPlatform(Platform.android_ios())
        .setAudience(Audience.newBuilder().addAudienceTarget(AudienceTarget.registrationId(registrationIds)).build())
        .setNotification(Notification.newBuilder()
            .addPlatformNotification(androidNotificationBuilder.build())
            .addPlatformNotification(iosNotificationBuilder.build())
            .build())
        .setOptions(channelOptions.getOptions())
        .build();
  }

  /**
   * 构建极光推送客户端
   *
   * @return
   */
  public JPushClient buildJPushClient() {
    Function<String, String> getConfiguration = key -> configurationService.get(String.class, key)
        .orElseThrow(() -> new BusinessException("极光推送通知配置错误: " + key + " 为空"));

    String masterSecret = getConfiguration.apply("notification.jpush.masterSecret");
    String appKey = getConfiguration.apply("notification.jpush.appKey");

    ClientConfig clientConfig = ClientConfig.getInstance();
    return new JPushClient(masterSecret, appKey, null, clientConfig);
  }


  /**
   * 拷贝对象值
   *
   * @param copyObj
   * @param targetObj
   */
  private void copyObjectValue(Object copyObj, Object targetObj) {
    for (Field copyField : copyObj.getClass().getDeclaredFields()) {
      try {
        copyField.setAccessible(true);
        Object value = copyField.get(copyObj);
        if (value == null) {
          continue;
        }

        Field targetField = targetObj.getClass().getDeclaredField(copyField.getName());
        targetField.setAccessible(true);
        targetField.set(targetObj, value);
      } catch (Exception copyFailure) {
        logger.debug("跳过无法复制的极光推送配置字段: {}", copyField.getName(), copyFailure);
      }
    }
  }
}
