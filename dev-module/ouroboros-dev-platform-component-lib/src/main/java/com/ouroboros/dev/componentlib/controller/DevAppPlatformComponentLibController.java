package com.ouroboros.dev.componentlib.controller;

import org.springframework.web.bind.annotation.*;

import io.vavr.control.Try;

import com.ouroboros.dev.componentlib.dto.DevAppPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.dto.ImportedPlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.service.DevAppPlatformComponentLibService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/platform-component-lib")
public class DevAppPlatformComponentLibController {
  private final DevAppPlatformComponentLibService devAppComponentLibService;

  public DevAppPlatformComponentLibController(DevAppPlatformComponentLibService devAppComponentLibService) {
    this.devAppComponentLibService = devAppComponentLibService;
  }

  /**
   * 获取应用的组件库配置
   */
  @GetMapping
  public WebApiResult<DevAppPlatformComponentLibDTO> get(@PathVariable String appName) {
    return Try.of(() -> devAppComponentLibService.getDevAppComponentLib(appName))
        .fold(
            ex -> WebApiResult.error(ex.getMessage()),
            WebApiResult::ok
        );
  }

  /**
   * 导入组件库到应用
   */
  @PostMapping
  public WebApiResult<String> importComponentLib(
      @PathVariable String appName,
      @RequestBody ImportedPlatformComponentLibDTO importDTO) {
    return Try.run(() -> devAppComponentLibService.importComponentLib(appName, importDTO))
        .fold(
            ex -> WebApiResult.error(ex.getMessage()),
            v -> WebApiResult.ok("组件库导入成功")
        );
  }

  /**
   * 从应用移除组件库
   */
  @DeleteMapping("/{componentLibName}")
  public WebApiResult<String> removeComponentLib(
      @PathVariable String appName,
      @PathVariable String componentLibName) {
    return Try.run(() -> devAppComponentLibService.removeComponentLib(appName, componentLibName))
        .fold(
            ex -> WebApiResult.error(ex.getMessage()),
            v -> WebApiResult.ok("组件库移除成功")
        );
  }
}
