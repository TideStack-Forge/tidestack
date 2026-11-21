package com.ouroboros.notification.internal.message.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import com.ouroboros.notification.adapter.NotificationChannelResult;
import com.ouroboros.common.service.ModelService;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.notification.internal.message.adapter.InternalMessageNotificationChannelOptions;
import com.ouroboros.notification.internal.message.service.model.NotificationInternalMessageModel;

/**
 * @author liansz
 */
public interface NotificationInternalMessageService extends ModelService<String, NotificationInternalMessageModel> {

  /**
   * 发送通知
   *
   * @param employeeIds
   * @param title
   * @param content
   * @param channelOptions
   */
  void sendNotification(List<String> employeeIds, String title, String content,
                        InternalMessageNotificationChannelOptions channelOptions) throws OuroborosException;

  /** Independently persists one stable channel unit; replay cannot change its employees or content. */
  default NotificationChannelResult sendNotificationWithResult(String deliveryKey,
      List<String> employeeIds, String title, String content, InternalMessageNotificationChannelOptions options) {
    return new NotificationChannelResult(
        NotificationChannelResult.Outcome.BLOCKED, "internal-message-reliable-service-unavailable");
  }

  /**
   * 查询未读数量
   *
   * @param employee
   * @return
   */
  Long unreadCount(String employee) throws OuroborosException;

  void notifyUnreadCount(Collection<String> employeeIds) throws OuroborosException;

  /**
   * 按主键标记已读
   *
   * @param id
   * @return
   */
  default Boolean markReadById(String employee, String id) throws OuroborosException {
    return markReadByIds(employee, Collections.singleton(id));
  }

  /**
   * 按主键列表标记已读
   *
   * @param ids
   * @return
   */
  Boolean markReadByIds(String employee, Collection<String> ids) throws OuroborosException;

  /**
   * 按员工标记已读
   *
   * @param employee
   * @return
   */
  Boolean markReadByEmployee(String employee) throws OuroborosException;
}
