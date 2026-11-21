package com.ouroboros.notification.controller;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.AddressNotificationDispatcher;
import com.ouroboros.notification.DispatchOptions;
import com.ouroboros.notification.EmployeeNotificationDispatcher;
import com.ouroboros.notification.controller.req.SendNotificationReq;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationController {

  @Autowired
  EmployeeNotificationDispatcher employeeNotificationDispatcher;

  @Autowired
  AddressNotificationDispatcher addressNotificationDispatcher;

  @Operation(
      summary = "发送通知",
      description = "按模板向员工或指定地址异步发送通知消息。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "通知已进入异步发送流程"),
      @ApiResponse(responseCode = "400", description = "通知模板或接收方参数不合法")
  })
  @PostMapping("/send")
  public WebApiResult<Boolean> send(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "通知发送请求", required = true)
      @RequestBody SendNotificationReq req) {
    String template = req.getTemplate();
    if (StringUtils.isBlank(template)) {
      return WebApiResult.error(400, "模板不能为空");
    }

    List<String> employeeIds = req.getEmployeeIds();
    Map<String, Collection<String>> addresses = req.getAddresses();
    if (ObjectUtils.isEmpty(employeeIds) && ObjectUtils.isEmpty(addresses)) {
      return WebApiResult.error(400, "员工和地址不能同时为空");
    }

    Map<String, Object> payload = req.getPayload() != null ? req.getPayload() : Collections.emptyMap();

    String currentEmployee = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);
    if (StringUtils.isBlank(currentEmployee)) {
      return WebApiResult.error(400, "当前用户不存在");
    }

    DispatchOptions dispatchOptions = req.getDispatchOptions() != null ? req.getDispatchOptions() : new DispatchOptions();
    dispatchOptions.setSendByEmployee(currentEmployee);

    if (ObjectUtils.isNotEmpty(employeeIds)) {
      CompletableFuture.runAsync(() -> employeeNotificationDispatcher.send(employeeIds, template, payload, dispatchOptions));
    }

    if (ObjectUtils.isNotEmpty(addresses)) {
      CompletableFuture.runAsync(() -> addressNotificationDispatcher.send(addresses, template, payload, dispatchOptions));
    }

    return WebApiResult.ok(true);
  }
}
