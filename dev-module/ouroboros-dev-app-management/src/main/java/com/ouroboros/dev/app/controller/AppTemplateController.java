package com.ouroboros.dev.app.controller;

import java.io.IOException;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ouroboros.dev.app.model.AppTemplateDTO;
import com.ouroboros.dev.app.service.AppTemplateService;
import com.ouroboros.model.AppInfo;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

/**
 * 应用模板控制器类
 * 处理应用模板相关的HTTP请求，包括获取、导入、导出、删除模板以及从模板创建应用等功能
 */
@RestController
@RequestMapping("/api/app-template")
public class AppTemplateController {
  // 依赖注入应用模板服务
  private final AppTemplateService appTemplateService;

  /**
   * 构造函数
   * 通过构造函数注入AppTemplateService
   *
   * @param appTemplateService 应用模板服务实例
   */
  public AppTemplateController(AppTemplateService appTemplateService) {
    this.appTemplateService = appTemplateService;
  }

  /**
   * 获取所有应用模板列表的接口方法
   *
   * @return ItemsWebApiResult<AppTemplateDTO> 返回包含所有应用模板的响应结果
   */
  @GetMapping
  public ItemsWebApiResult<AppTemplateDTO> list() {
    // 调用服务层获取所有应用模板数据
    var templates = appTemplateService.getAll();
    // 返回成功响应，包含获取到的模板列表
    return ItemsWebApiResult.ok(templates);
  }

  /**
   * 导入模板接口
   *
   * @param file         上传的模板文件
   * @param templateName 模板名称
   * @return 返回操作结果
   * @throws IOException 文件读取异常
   */
  @PostMapping("/import")
  public WebApiResult<Void> importTemplate(MultipartFile file,
                                           @RequestParam(required = false) String templateName) throws IOException {
    // 调用服务层方法导入模板，传入模板名称和文件输入流
    var result = appTemplateService.importTemplate(templateName, file.getInputStream());
    // 封装并返回操作结果
    return WebApiResult.of(result);
  }

  /**
   * 导出模板接口
   * 根据模板名称导出对应的模板文件
   *
   * @param templateName 模板名称，用于指定要导出的模板
   * @return ResponseEntity<Resource> 返回响应实体，包含导出的模板资源或错误响应
   */
  @GetMapping("/{templateName}/export")
  public ResponseEntity<Resource> exportTemplate(@PathVariable String templateName) {
    // 调用服务层方法导出模板
    var result = appTemplateService.exportTemplate(templateName);
    if (result.isFailure()) {
      // 如果导出失败，返回400错误响应
      return ResponseEntity.badRequest().build();
    }

    var exported = result.get();
    // 获取导出的文件资源
    return ResponseEntity.ok()
        // 返回成功的响应，设置响应头和文件资源
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + templateName + "-template.zip")
        .header(HttpHeaders.CONTENT_TYPE, "application/zip")
        .body(new InputStreamResource(exported));
  }

  /**
   * 删除指定模板的接口方法
   *
   * @param templateName 模板名称，通过路径变量传递
   * @return 返回操作结果的WebApiResult，包含操作是否成功的信息
   */
  @DeleteMapping("/{templateName}")
  public WebApiResult<Void> deleteTemplate(@PathVariable String templateName) {
    // 调用服务层方法删除模板，并将结果封装为WebApiResult返回
    var result = appTemplateService.deleteTemplate(templateName);
    return WebApiResult.of(result);
  }

  /**
   * 根据模板创建应用接口
   *
   * @param templateName                 模板名称，从URL路径中获取
   * @param createAppFromTemplateRequest 创建应用的请求参数，包含应用名称等信息
   * @return 返回操作结果，成功则返回成功状态，失败则返回错误信息
   */
  @PostMapping("/{templateName}/create-app")
  public WebApiResult<Void> createAppFromTemplate(@PathVariable String templateName, @RequestBody CreateAppFromTemplateRequest createAppFromTemplateRequest) {
    // 从请求参数中获取应用名称
    var appInfo = new AppInfo();

    appInfo.setName(createAppFromTemplateRequest.getAppName())
        .setTitle(createAppFromTemplateRequest.getAppTitle())
        .setIcon(createAppFromTemplateRequest.getAppIcon())
        .setDescription(createAppFromTemplateRequest.getAppDescription());

    // 调用服务层方法根据模板创建应用
    var result = appTemplateService.createAppByTemplate(appInfo, templateName);
    // 返回操作结果
    return WebApiResult.of(result);
  }

  /**
   * 从模板创建应用的请求类
   * 用于封装从模板创建应用所需的请求数据
   */
  static public class CreateAppFromTemplateRequest {
    // 应用名称属性
    private String appName;

    private String appTitle;

    private String appIcon;

    private String appDescription;

    /**
     * 获取应用名称
     *
     * @return 应用的名称字符串
     */
    public String getAppName() {
      return appName;
    }

    /**
     * 设置应用名称
     *
     * @param appName 要设置的应用名称字符串
     */
    public void setAppName(String appName) {
      this.appName = appName;
    }

    public String getAppTitle() {
      return appTitle;
    }

    public void setAppTitle(String appTitle) {
      this.appTitle = appTitle;
    }

    public String getAppIcon() {
      return appIcon;
    }

    public void setAppIcon(String appIcon) {
      this.appIcon = appIcon;
    }

    public String getAppDescription() {
      return appDescription;
    }

    public void setAppDescription(String appDescription) {
      this.appDescription = appDescription;
    }
  }
}
