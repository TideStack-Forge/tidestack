package com.ouroboros.dev.app.controller;

import java.util.stream.Collectors;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.dev.app.model.AppVersionDTO;
import com.ouroboros.dev.app.service.AppVersionService;
import com.ouroboros.vcs.VCSManagerCenter;
import com.ouroboros.vcs.file.inspector.DescriptionNode;
import com.ouroboros.vcs.file.inspector.FileTreeBuilder;
import com.ouroboros.vcs.versioning.ChangeLog;
import com.ouroboros.vcs.versioning.VersionState;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 应用版本控制器，提供应用版本相关的REST API接口
 * 处理版本列表查询、详情查看、文件列表、版本创建和版本导出等功能
 */
@RestController
@RequestMapping("/api/dev/{appName}/version")
public class AppVersionController {
  // 应用版本服务，用于处理业务逻辑
  private final AppVersionService appVersionService;
  private final VCSManagerCenter vcsManagerCenter;
  /**
   * 构造函数，通过依赖注入方式获取AppVersionService实例
   * @param appVersionService 应用版本服务
   */
  public AppVersionController(AppVersionService appVersionService, VCSManagerCenter vcsManagerCenter) {
    this.appVersionService = appVersionService;
    this.vcsManagerCenter = vcsManagerCenter;
  }

  /**
   * 获取指定应用的所有版本列表
   * @param appName 应用名称，从路径变量中获取
   * @return 返回包含所有版本信息的ItemsWebApiResult结果对象
   */
  @GetMapping
  public ItemsWebApiResult<AppVersionDTO> list(@PathVariable String appName) {
    var versions = appVersionService.getAll(appName);
    return ItemsWebApiResult.ok(versions);
  }

  /**
   * 获取指定应用的特定版本详情
   * @param appName 应用名称，从路径变量中获取
   * @param version 版本号，从路径变量中获取
   * @return 返回包含版本详情的WebApiResult结果对象
   */
  @GetMapping("/{version}")
  public WebApiResult<AppVersionDTO> detail(@PathVariable String appName, @PathVariable String version) {
    var versionOpt = appVersionService.getOne(appName, version);
    return WebApiResult.of(versionOpt);
  }

  /**
   * 获取当前版本的文件列表
   * @param appName 应用名称，从路径变量中获取
   * @param onlyUncommitted 是否只显示未提交的文件，默认为true
   * @return 返回包含文件名的ItemsWebApiResult结果对象
   */
  @GetMapping("/current-files")
  public ItemsWebApiResult<? extends DescriptionNode> listCurrentFiles(@PathVariable String appName, @RequestParam(required = false, defaultValue = "true") boolean onlyUncommitted) {
    var files = appVersionService.getAllFiles(appName).stream()
        .filter(f -> !onlyUncommitted || f.getVersionState() != VersionState.NOTCHANGED)
        .collect(Collectors.toList());
    var tree = FileTreeBuilder.build(files);
    return ItemsWebApiResult.ok(tree);
  }

  /**
   * 获取最近修改记录
   * @param appName 应用名称，从路径变量中获取
   * @param limit 最大记录数，默认为10
   * @param skip 跳过的记录数，默认为0
   */
  @GetMapping("/recent-change-log")
  public ItemsWebApiResult<ChangeLog> recentChangeLog(@PathVariable String appName,
                                                      @RequestParam(required = false, defaultValue = "0") int skip,
                                                      @RequestParam(required = false, defaultValue = "10") int limit) {
    var vcsManager = vcsManagerCenter.getVcsManager(appName);
    var changeLog = vcsManager.getChangeLogs(".", skip, limit);
    return ItemsWebApiResult.ok(changeLog);
  }

  /**
   * 创建新的应用版本
   * @param appName 应用名称，从路径变量中获取
   * @param version 版本信息，从请求体中获取
   * @return 返回操作结果的WebApiResult对象
   */
  @PostMapping
  public WebApiResult<Void> create(@PathVariable String appName, @RequestBody AppVersionDTO version) {
    return WebApiResult.of(appVersionService.create(appName, version));
  }

  /**
   * 导出指定版本
   * @param appName 应用名称，从路径变量中获取
   * @param version 版本号，从路径变量中获取
   * @return 返回包含导出文件的ResponseEntity对象
   */
  @GetMapping("/{version}/export")
  public ResponseEntity<Resource> export(@PathVariable String appName, @PathVariable String version) {
    var exported = appVersionService.exportVersion(appName, version);
    if (exported.isFailure()) {
      return ResponseEntity.badRequest().build();
    }
    var result = exported.get();
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + appName + "-" + version + ".zip")
        .header(HttpHeaders.CONTENT_TYPE, "application/zip")
        .body(new InputStreamResource(result));
  }
}
