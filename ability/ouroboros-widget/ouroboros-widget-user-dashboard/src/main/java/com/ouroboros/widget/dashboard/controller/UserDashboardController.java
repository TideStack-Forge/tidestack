package com.ouroboros.widget.dashboard.controller;

import java.util.Collections;
import java.util.Map;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.widget.Dashboard;
import com.ouroboros.widget.dashboard.service.UserDashboardService;

@RestController
@RequestMapping("/api/user-dashboard")
public class UserDashboardController {
  private UserDashboardService userDashboardService;

  public UserDashboardController(UserDashboardService userDashboardService) {
    this.userDashboardService = userDashboardService;
  }

  @Operation(
      summary = "初始化当前用户仪表盘",
      description = "获取当前用户的仪表盘布局数据。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户仪表盘")
  })
  @GetMapping("/init")
  public WebApiResult<Map<String, Dashboard>> init() {
    var dashboard = userDashboardService.getUserDashboard(getUserId());
    return WebApiResult.ok(Collections.singletonMap("dashboard", dashboard));
  }

  @Operation(
      summary = "保存当前用户仪表盘",
      description = "保存当前用户的仪表盘布局数据。",
      tags = {"widget"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "用户仪表盘保存成功"),
      @ApiResponse(responseCode = "400", description = "仪表盘数据不合法")
  })
  @PostMapping("/save")
  public WebApiResult<Void> save(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "用户仪表盘保存请求", required = true)
      @RequestBody SaveDashboardRequest saveDashboardRequest) {
    var result = userDashboardService.updateUserDashboard(getUserId(), saveDashboardRequest.getDashboard());
    return WebApiResult.of(result);
  }

  public static class SaveDashboardRequest {
    private Dashboard dashboard;

    public Dashboard getDashboard() {
      return dashboard;
    }

    public SaveDashboardRequest setDashboard(Dashboard dashboard) {
      this.dashboard = dashboard;
      return this;
    }
  }

  private String getUserId() {
    return CurrentSecurityContextHolder
        .findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
  }
}
