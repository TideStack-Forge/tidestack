package com.ouroboros.app.notification;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.notification.aliyun.call.adapter.constant.AliyunCallConstant;
import com.ouroboros.notification.aliyun.sms.adapter.constant.AliyunSmsConstant;
import com.ouroboros.notification.email.adapter.constant.EmailConstant;
import com.ouroboros.notification.employee.NotificationEmployeeAddressParser;
import com.ouroboros.notification.employee.NotificationChannelRecipients;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.internal.message.adapter.constant.InternalMessageConstant;
import com.ouroboros.notification.jpush.adapter.constant.JPushConstant;
import com.ouroboros.notification.service.NotificationChannelEmployeeMapService;
import com.ouroboros.notification.service.model.NotificationChannelEmployeeMapModel;
import com.ouroboros.notification.wechat.adapter.constant.WechatConstant;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;

/**
 * @author liansz
 */
@Component
public class NotificationEmployeeAddressParserImpl implements NotificationEmployeeAddressParser {

  @Autowired(required = false)
  private List<com.ouroboros.notification.adapter.NotificationAdapterSupplier> managedSuppliers = List.of();

  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Autowired
  private NotificationChannelEmployeeMapService channelEmployeeMapService;

  @Override
  public Map<String, Collection<String>> parse(Collection<String> employeeIds, Collection<String> channels) {
    var resolved = resolve(employeeIds, channels, Map.of());
    Map<String, Collection<String>> result = new LinkedHashMap<>();
    resolved.forEach((channel, recipients) -> result.put(channel, recipients.addresses().values().stream()
        .flatMap(Collection::stream).distinct().toList()));
    return result;
  }

  @Override
  public Map<String, NotificationChannelRecipients.Resolution> resolve(Collection<String> employeeIds,
      Collection<String> channels, Map<String, Map<String, Object>> channelOptions) {
    var result = new LinkedHashMap<String, NotificationChannelRecipients.Resolution>();
    for (String channel : channels) {
      var provider = NotificationAdapterCenter.findSupplier(channel, managedSuppliers).flatMap(supplier -> supplier.getEmployeeRecipients());
      var options = channelOptions.getOrDefault(channel, Map.of());
      if (provider.isPresent()) {
        result.put(channel, provider.get().resolve(employeeIds, options));
      } else {
        var addresses = new LinkedHashMap<String, List<String>>();
        for (String employee : employeeIds) {
          addresses.put(employee, List.copyOf(legacyAddresses(List.of(employee), List.of(channel)).getOrDefault(channel, List.of())));
        }
        result.put(channel, new NotificationChannelRecipients.Resolution(options, addresses));
      }
    }
    return Map.copyOf(result);
  }

  private Map<String, Collection<String>> legacyAddresses(Collection<String> employeeIds, Collection<String> channels) {
    Map<String, Collection<String>> result = new HashMap<>();
    for (String channel : channels) {
      switch (channel) {
        case InternalMessageConstant.ADAPTER_NAME -> result.put(channel, employeeIds);

        case JPushConstant.ADAPTER_NAME, WechatConstant.ADAPTER_NAME -> {
          var addresses = channelEmployeeMapService.searchByEmployeeIds(channel, new ArrayList<>(employeeIds))
              .stream()
              .map(NotificationChannelEmployeeMapModel::getAddress)
              .collect(Collectors.toList());
          result.put(channel, addresses);
        }

        case EmailConstant.ADAPTER_NAME -> {
          result.put(
              channel, employeeAggregator.getByIds(employeeIds).stream()
                  .map(Employee::getEmail)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList())
          );
        }

        case AliyunSmsConstant.ADAPTER_NAME, AliyunCallConstant.ADAPTER_NAME -> {
          result.put(
              channel,
              employeeAggregator.getByIds(employeeIds).stream()
                  .map(Employee::getMobile)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList())
          );
        }
      }
    }

    return result;
  }
}
