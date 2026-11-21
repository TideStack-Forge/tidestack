package com.ouroboros.notification;

import java.util.Collection;
import java.util.Map;

/**
 * @author liansz
 */
public interface AddressNotificationDispatcher {

  /**
   * 发送通知
   *
   * @param addressMap Map<channel, Collection<address>>
   * @param template
   * @param payload
   */
  default void send(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload) {
    send(addressMap, template, payload, null);
  }

  /**
   * 发送通知
   *
   * @param addressMap Map<channel, Collection<address>>
   * @param template
   * @param payload
   */
  default DispatchResult sendWithResult(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload, DispatchOptions options) {
    send(addressMap, template, payload, options);
    return DispatchResult.unknown();
  }

  void send(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload, DispatchOptions options);
}
