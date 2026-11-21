package com.ouroboros.usersetting.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.security.PermitAll;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.AppContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.usersetting.controller.response.UserSettingResponse;
import com.ouroboros.usersetting.controller.request.SettingValueRequest;
import com.ouroboros.usersetting.service.UserSettingProvider;
import com.ouroboros.usersetting.service.UserSettingService;
import com.ouroboros.web.WebApiResult;

@RequestMapping("/api/user-setting")
@RestController
public class UserSettingController {
  List<UserSettingProvider> userSettingProviders;
  UserSettingService userSettingService;
  AppContext appContext;

  public UserSettingController(List<UserSettingProvider> userSettingProviders,
      UserSettingService userSettingService, AppContext appContext) {
    this.userSettingProviders = userSettingProviders;
    this.userSettingService = userSettingService;
    this.appContext = appContext;
  }

  @GetMapping
  @PermitAll
  @Operation(
      summary = "获取当前用户的自动加载设置",
      description = "返回当前用户在当前客户端下标记为自动加载的设置集合。",
      tags = {"user-setting"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回用户设置")
  })
  public WebApiResult<Map<String, Object>> getAllSettings() {
    var userId = getUserId();
    var clientType = getClientType();
    var settings = userSettingProviders
        .stream()
        .flatMap(provider -> provider.getAllSettings(userId, clientType).stream())
        .filter(setting -> setting.getIsAutoLoad() == null || setting.getIsAutoLoad())
        .collect(() -> new HashMap<String, Object>(),
            (map, setting) -> map.put(setting.getSettingName(), setting.getSettingValue()),
            HashMap::putAll);
    return WebApiResult.ok(settings);
  }

  @GetMapping("/get")
  @PermitAll
  @Operation(
      summary = "获取单个用户设置",
      description = "根据设置名称查询当前用户在当前客户端下的设置项。",
      tags = {"user-setting"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回设置项"),
      @ApiResponse(responseCode = "404", description = "设置项不存在")
  })
  public WebApiResult<UserSettingResponse> getSetting(
      @Parameter(description = "设置名称", required = true)
      @RequestParam(value = "settingName", required = true) String settingName) {
    var userId = getUserId();
    var clientType = getClientType();
    var setting = userSettingProviders
        .stream()
        .map(provider -> provider.getSetting(userId, clientType, settingName))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(s -> new UserSettingResponse(settingName, s.getSettingValue()))
        .findFirst();
    return WebApiResult.of(404, "设置项不存在", setting);
  }

  @GetMapping("/get-value")
  @PermitAll
  @Operation(
      summary = "获取用户设置值",
      description = "根据设置名称查询当前用户设置项的原始值。",
      tags = {"user-setting"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回设置值"),
      @ApiResponse(responseCode = "404", description = "设置项不存在")
  })
  public WebApiResult<Object> getSettingValue(
      @Parameter(description = "设置名称", required = true)
      @RequestParam(value = "settingName", required = true) String settingName) {
    return getSetting(settingName).map(UserSettingResponse::getSettingValue);
  }

  @PatchMapping
  @PermitAll
  @Operation(
      summary = "保存用户设置",
      description = "保存或更新当前用户在当前客户端下的设置值。",
      tags = {"user-setting"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "设置保存成功"),
      @ApiResponse(responseCode = "400", description = "设置参数不合法")
  })
  public WebApiResult<Void> saveSetting(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "待保存的用户设置", required = true)
      @RequestBody SettingValueRequest settingRequest) {
    var userId = getUserId();
    var clientType = getClientType();
    var settingName = settingRequest.getSettingName();
    var rawSettingValue = settingRequest.getRawSettingValue();
    var isAutoLoad = settingRequest.getIsAutoLoad();
    var result = userSettingService.saveSetting(userId, clientType, settingName, rawSettingValue, isAutoLoad);
    return WebApiResult.of(result);
  }

  @DeleteMapping
  @PermitAll
  @Operation(
      summary = "删除用户设置",
      description = "删除当前用户在当前客户端下的指定设置项。",
      tags = {"user-setting"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "设置删除成功"),
      @ApiResponse(responseCode = "404", description = "设置项不存在")
  })
  public WebApiResult<Void> deleteSetting(
      @Parameter(description = "设置名称", required = true)
      @RequestParam("settingName") String settingName) {
    var userId = getUserId();
    var clientType = getClientType();
    var exists = userSettingProviders.stream()
        .map(provider -> provider.getSetting(userId, clientType, settingName))
        .anyMatch(Optional::isPresent);
    if (!exists) {
      return WebApiResult.error(404, "设置项不存在");
    }
    var setting = userSettingService.deleteSetting(userId, clientType, settingName);
    return WebApiResult.of(setting);
  }

  private String getUserId() {
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> identity.getUserIdentity())
        .orElse(null);
  }

  private String getClientType() {
    return CurrentSecurityContextHolder.getClientType();
  }
}
