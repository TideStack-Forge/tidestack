package com.ouroboros.notification.controller.req;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.ouroboros.notification.DispatchOptions;

/**
 * @author liansz
 */
public class SendNotificationReq {

  /**
   * 模板
   */
  private String template;

  /**
   * 员工id
   */
  private List<String> employeeIds;

  /**
   * 地址
   * Map<channel, List<address>>
   */
  private Map<String, Collection<String>> addresses;

  /**
   * 参数载荷
   */
  private Map<String, Object> payload;

  /**
   * 发送选项
   */
  private DispatchOptions dispatchOptions;

  public String getTemplate() {
    return template;
  }

  public void setTemplate(String template) {
    this.template = template;
  }

  public List<String> getEmployeeIds() {
    return employeeIds;
  }

  public void setEmployeeIds(List<String> employeeIds) {
    this.employeeIds = employeeIds;
  }

  public Map<String, Collection<String>> getAddresses() {
    return addresses;
  }

  public void setAddresses(Map<String, Collection<String>> addresses) {
    this.addresses = addresses;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public DispatchOptions getDispatchOptions() {
    return dispatchOptions;
  }

  public void setDispatchOptions(DispatchOptions dispatchOptions) {
    this.dispatchOptions = dispatchOptions;
  }
}
