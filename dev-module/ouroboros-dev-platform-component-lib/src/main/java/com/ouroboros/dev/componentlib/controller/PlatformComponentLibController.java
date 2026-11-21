package com.ouroboros.dev.componentlib.controller;

import java.io.File;
import java.nio.file.Files;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.control.Try;

import com.ouroboros.dev.componentlib.dto.ComponentDefineDTO;
import com.ouroboros.dev.componentlib.dto.PlatformComponentLibDTO;
import com.ouroboros.dev.componentlib.service.PlatformComponentLibService;
import com.ouroboros.model.AppInfo;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/platform-component-lib")
public class PlatformComponentLibController {
  private final PlatformComponentLibService componentLibService;

  public PlatformComponentLibController(PlatformComponentLibService componentLibService) {
    this.componentLibService = componentLibService;
  }

  /**
   * 获取所有组件库
   */
  @GetMapping
  public ItemsWebApiResult<PlatformComponentLibDTO> getAll() {
    return Try.of(() -> componentLibService.getAll())
        .fold(
            ex -> ItemsWebApiResult.error(ex.getMessage()),
            ItemsWebApiResult::ok
        );
  }

  /**
   * 获取指定组件库
   */
  @GetMapping("/{name}")
  public WebApiResult<PlatformComponentLibDTO> getOne(@PathVariable String name) {
    return WebApiResult.of("组件库不存在", componentLibService.get(name));
  }

  /**
   * 获取所有组件定义
   */
  @GetMapping("/component-defines")
  public ItemsWebApiResult<ComponentDefineDTO> getAllComponentDefines() {
    return Try.of(() -> componentLibService.getAllComponentDefine())
        .fold(
            ex -> ItemsWebApiResult.error(ex.getMessage()),
            ItemsWebApiResult::ok
        );
  }

  /**
   * 获取组件库相关的应用列表
   */
  @GetMapping("/{name}/related-apps")
  public ItemsWebApiResult<AppInfo> getRelatedApps(@PathVariable String name) {
    return componentLibService.get(name)
        .map(lib -> Try.of(() -> componentLibService.getRelatedApps(lib))
            .fold(
                ex -> ItemsWebApiResult.<AppInfo>error(ex.getMessage()),
                ItemsWebApiResult::ok
            ))
        .orElseGet(() -> ItemsWebApiResult.error("组件库不存在"));
  }

  /**
   * 检查组件库包
   */
  @PostMapping("/inspect")
  public WebApiResult<PlatformComponentLibDTO> inspectPackage(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return WebApiResult.error("文件不能为空");
    }

    return Try.of(() -> {
      // 保存临时文件
      File tempFile = Files.createTempFile("component-lib-", ".zip").toFile();
      try {
        file.transferTo(tempFile);
        Try<PlatformComponentLibDTO> result = componentLibService.inspectComponentLibPackage(tempFile);
        return result.get();
      } finally {
        // 删除临时文件
        tempFile.delete();
      }
    }).fold(
        ex -> WebApiResult.error(ex.getMessage()),
        WebApiResult::ok
    );
  }

  /**
   * 安装组件库
   */
  @PostMapping
  public WebApiResult<String> install(@RequestParam("file") MultipartFile file) {
    if (file.isEmpty()) {
      return WebApiResult.error("文件不能为空");
    }

    return Try.of(() -> {
      // 保存临时文件
      File tempFile = Files.createTempFile("component-lib-", ".zip").toFile();
      try {
        file.transferTo(tempFile);
        componentLibService.installComponentLibPackage(tempFile).get();
        return "组件库安装成功";
      } finally {
        // 删除临时文件
        tempFile.delete();
      }
    }).fold(
        ex -> WebApiResult.error(ex.getMessage()),
        WebApiResult::ok
    );
  }

  /**
   * 卸载组件库
   */
  @DeleteMapping("/{name}")
  public WebApiResult<String> uninstall(@PathVariable String name) {
    return componentLibService.get(name)
        .map(lib -> componentLibService.uninstallComponentLib(lib)
            .fold(
                ex -> WebApiResult.<String>error(ex.getMessage()),
                v -> WebApiResult.ok("组件库卸载成功")
            ))
        .orElseGet(() -> WebApiResult.error("组件库不存在"));
  }
}
