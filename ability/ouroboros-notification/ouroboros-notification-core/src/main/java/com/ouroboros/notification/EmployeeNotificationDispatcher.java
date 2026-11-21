package com.ouroboros.notification;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * @author liansz
 */
public interface EmployeeNotificationDispatcher {

  /**
   * 发送通知
   *
   * @param employeeId
   * @param template
   * @param payload
   */
  default void send(String employeeId, String template, Map<String, Object> payload) {
    send(employeeId, template, payload, null);
  }

  /**
   * 发送通知
   *
   * @param employeeId
   * @param template
   * @param payload
   * @param options
   */
  default void send(String employeeId, String template, Map<String, Object> payload, DispatchOptions options) {
    send(Collections.singletonList(employeeId), template, payload, options);
  }

  /**
   * 发送通知
   *
   * @param employeeIds
   * @param template
   * @param payload
   */
  default void send(Collection<String> employeeIds, String template, Map<String, Object> payload) {
    send(employeeIds, template, payload, null);
  }

  /** 返回每个渠道的实际投递结果，供可靠 outbox 调用方确认状态。 */
  default DispatchResult sendWithResult(Collection<String> employeeIds, String template,
      Map<String, Object> payload, DispatchOptions options) {
    send(employeeIds, template, payload, options);
    return DispatchResult.unknown();
  }

  /**
   * 发送通知
   *
   * @param employeeIds
   * @param template
   * @param payload
   */
  void send(Collection<String> employeeIds, String template, Map<String, Object> payload, DispatchOptions options);
  /** Reliable event delivery; requires an explicit, current recipient/disclosure policy. */
  default NotificationDispatchResult sendWithResult(NotificationDispatchRequest request, NotificationDeliveryPolicy policy) {
    throw new com.ouroboros.exception.BusinessException("当前通知 Dispatcher 不支持可靠发送结果");
  }
}
