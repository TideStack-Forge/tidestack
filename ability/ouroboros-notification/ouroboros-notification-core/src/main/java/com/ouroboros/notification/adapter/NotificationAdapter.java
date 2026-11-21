package com.ouroboros.notification.adapter;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import com.ouroboros.exception.InternalException;
import com.ouroboros.notification.exception.NotificationSendException;

/**
 * @author liansz
 */
public interface NotificationAdapter<D extends NotificationChannelSchema, O extends NotificationChannelOptions> {

  /**
   * 发送通知（单发）
   *
   * @param addresses     地址
   * @param channelSchema 渠道架构
   * @param payload       载荷
   * @throws NotificationSendException 发送异常
   */
  default void send(String addresses, D channelSchema, Map<String, Object> payload) throws NotificationSendException {
    send(addresses, channelSchema, payload, null);
  }

  /**
   * 发送通知（单发）
   *
   * @param addresses      地址
   * @param channelSchema  渠道架构
   * @param payload        载荷
   * @param channelOptions 渠道选项参数
   * @throws NotificationSendException 发送异常
   */
  default void send(String addresses, D channelSchema, Map<String, Object> payload, O channelOptions) throws NotificationSendException {
    send(Collections.singletonList(addresses), channelSchema, payload, channelOptions);
  }

  /**
   * 发送通知（群发）
   *
   * @param addresses     地址
   * @param channelSchema 渠道架构
   * @param payload       载荷
   * @throws NotificationSendException 发送异常
   */
  default void send(Collection<String> addresses, D channelSchema, Map<String, Object> payload) throws NotificationSendException {
    send(addresses, channelSchema, payload, null);
  }

  /**
   * 发送通知（群发）
   *
   * @param addresses      地址
   * @param channelSchema  渠道架构
   * @param payload        载荷
   * @param channelOptions 渠道选项参数
   * @throws NotificationSendException 发送异常
   */
  void send(Collection<String> addresses, D channelSchema, Map<String, Object> payload, O channelOptions) throws NotificationSendException;

  /**
   * Reliable dispatch entry for adapters with structured provider receipts. The stable delivery key is reusable.
   * Empty explicitly means unsupported and no send was attempted; the Dispatcher calls the original proxied send.
   * Do not invoke send from this default method: self invocation can bypass the adapter's transaction advice.
   */
  default java.util.Optional<NotificationChannelResult> sendWithResult(String deliveryKey, Collection<String> addresses,
      D channelSchema, Map<String, Object> payload, O channelOptions) {
    return java.util.Optional.empty();
  }

  /** A persisted recipient route is supplied by the dispatcher, separately from editable channel options. */
  default java.util.Optional<NotificationChannelResult> sendWithResult(String deliveryKey, Collection<String> addresses,
      D channelSchema, Map<String, Object> payload, O channelOptions, Map<String, Object> routingContext) {
    return java.util.Optional.of(new NotificationChannelResult(NotificationChannelResult.Outcome.BLOCKED, "notification-routing-context-unsupported"));
  }

  /**
   * 获取渠道架构类型
   *
   * @return
   */
  default Class<? extends D> getChannelSchemaClass() {
    Type type = ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[0];
    try {
      return (Class<? extends D>) Class.forName(type.getTypeName());
    } catch (ClassNotFoundException e) {
      throw new InternalException("渠道架构类型加载失败", e);
    }
  }

  /**
   * 获取渠道定义类型
   *
   * @return
   */
  default Class<? extends O> getChannelOptionsClass() {
    Type type = ((ParameterizedType) getClass().getGenericInterfaces()[0]).getActualTypeArguments()[1];
    try {
      return (Class<? extends O>) Class.forName(type.getTypeName());
    } catch (ClassNotFoundException e) {
      throw new InternalException("渠道选项类型加载失败", e);
    }
  }
}
