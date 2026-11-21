package com.ouroboros.notification.contributor;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.notification.AddressNotificationDispatcher;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.script.ScriptContextContributor;

/**
 * @author liansz
 */
@Component
public class NotificationContextContributor {

  @Autowired
  private AddressNotificationDispatcher addressNotificationDispatcher;

  @Autowired
  private EmployeeNotificationDispatcher employeeNotificationDispatcher;

  public NotificationContextContributor() {
    Proxy.delegate = this;
  }

  public void sendByAddress(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload) {
    addressNotificationDispatcher.send(addressMap, template, payload, null);
  }

  public void sendByAddress(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload,
                            DispatchOptions options) {
    addressNotificationDispatcher.send(addressMap, template, payload, options);
  }

  public void sendByEmployee(String employeeId, String template, Map<String, Object> payload) {
    employeeNotificationDispatcher.send(employeeId, template, payload);
  }

  public void sendByEmployee(String employeeId, String template, Map<String, Object> payload, DispatchOptions options) {
    employeeNotificationDispatcher.send(employeeId, template, payload, options);
  }

  public void sendByEmployee(Collection<String> employeeIds, String template, Map<String, Object> payload) {
    employeeNotificationDispatcher.send(employeeIds, template, payload);
  }

  public void sendByEmployee(Collection<String> employeeIds, String template, Map<String, Object> payload, DispatchOptions options) {
    employeeNotificationDispatcher.send(employeeIds, template, payload, options);
  }

  public static class Proxy implements ScriptContextContributor {

    private static NotificationContextContributor delegate;

    @Override
    public Map<String, Object> getBindings() {
      Map<String, Object> bindings = new HashMap<>();
      bindings.put("NotificationDispatcher", this);
      return bindings;
    }

    public void sendByAddress(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload) {
      delegate.sendByAddress(addressMap, template, payload);
    }

    public void sendByAddress(Map<String, Collection<String>> addressMap, String template, Map<String, Object> payload,
                              DispatchOptions options) {
      delegate.sendByAddress(addressMap, template, payload, options);
    }

    public void sendByEmployee(String employeeId, String template, Map<String, Object> payload) {
      delegate.sendByEmployee(employeeId, template, payload);
    }

    public void sendByEmployee(String employeeId, String template, Map<String, Object> payload, DispatchOptions options) {
      delegate.sendByEmployee(employeeId, template, payload, options);
    }

    public void sendByEmployee(Collection<String> employeeIds, String template, Map<String, Object> payload) {
      delegate.sendByEmployee(employeeIds, template, payload);
    }

    public void sendByEmployee(Collection<String> employeeIds, String template, Map<String, Object> payload, DispatchOptions options) {
      delegate.sendByEmployee(employeeIds, template, payload, options);
    }
  }
}
