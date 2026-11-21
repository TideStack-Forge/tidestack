package com.ouroboros.notification.service;

import java.util.List;

import com.ouroboros.common.service.ModelService;
import com.ouroboros.notification.service.model.NotificationChannelEmployeeMapModel;

public interface NotificationChannelEmployeeMapService extends ModelService<String, NotificationChannelEmployeeMapModel> {

  void bind(String channel, String employeeId, String channelId, String remarks);

  void unbind(String channel, String employeeId);

  NotificationChannelEmployeeMapModel getByEmployeeId(String channel, String employeeId);

  List<? extends NotificationChannelEmployeeMapModel> searchByEmployeeIds(String channel, List<String> employeeIds);
}
