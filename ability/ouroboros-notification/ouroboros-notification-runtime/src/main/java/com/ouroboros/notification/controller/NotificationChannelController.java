package com.ouroboros.notification.controller;

import jakarta.annotation.security.PermitAll;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.notification.controller.req.ChannelBindingReq;
import com.ouroboros.notification.service.NotificationChannelEmployeeMapService;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.web.WebApiResult;

/**
 * @author liansz
 */
@RestController
@RequestMapping("/api/notification/{channel}")
public class NotificationChannelController {

  @Autowired
  NotificationChannelEmployeeMapService service;

  @PermitAll
  @Operation(
      summary = "绑定通知渠道地址",
      description = "为当前登录用户绑定指定通知渠道的接收地址。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "渠道地址绑定成功"),
      @ApiResponse(responseCode = "400", description = "绑定参数不合法")
  })
  @PostMapping("/employee/bind")
  public WebApiResult<?> bind(
      @Parameter(description = "通知渠道名称", required = true) @PathVariable("channel") String channel,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "渠道绑定请求", required = true)
      @RequestBody ChannelBindingReq req) {
    String employeeId = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);
    if (StringUtils.isBlank(employeeId)) {
      return WebApiResult.error(400, "无法找到当前登陆用户");
    }

    String address = req.getAddress();
    if (StringUtils.isBlank(address)) {
      return WebApiResult.error(400, "address 不能为空");
    }

    String remarks = req.getRemarks();
    if (StringUtils.isBlank(remarks)) {
      return WebApiResult.error(400, "remarks 不能为空");
    }
    service.bind(channel, employeeId, address, remarks);
    return WebApiResult.ok();
  }

  @PermitAll
  @Operation(
      summary = "解绑通知渠道地址",
      description = "解除当前登录用户在指定通知渠道上的绑定地址。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "渠道地址解绑成功"),
      @ApiResponse(responseCode = "400", description = "解绑参数不合法")
  })
  @PostMapping("/employee/unbind")
  public WebApiResult<?> unbind(
      @Parameter(description = "通知渠道名称", required = true) @PathVariable("channel") String channel) {
    String employeeId = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);
    if (StringUtils.isBlank(employeeId)) {
      return WebApiResult.error(400, "无法找到当前登陆用户");
    }

    service.unbind(channel, employeeId);
    return WebApiResult.ok();
  }

  @PermitAll
  @Operation(
      summary = "获取通知渠道绑定信息",
      description = "查询当前登录用户在指定通知渠道上的绑定信息。",
      tags = {"notification"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回绑定信息"),
      @ApiResponse(responseCode = "400", description = "查询参数不合法")
  })
  @GetMapping("/employee/bind/info")
  public WebApiResult<?> bindInfo(
      @Parameter(description = "通知渠道名称", required = true) @PathVariable("channel") String channel) {
    String employeeId = OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getEmployee)
        .map(Employee::getId)
        .orElse(null);
    if (StringUtils.isBlank(employeeId)) {
      return WebApiResult.error(400, "无法找到当前登陆用户");
    }

    return WebApiResult.ok(service.getByEmployeeId(channel, employeeId));
  }
}
