package com.ouroboros.dev.app.controller;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.ouroboros.dev.app.model.AppInfoWithMeta;
import com.ouroboros.dev.app.model.AppTemplateDTO;
import com.ouroboros.dev.app.service.AppCategoryService;
import com.ouroboros.dev.app.service.AppManagementService;
import com.ouroboros.dev.app.service.AppTemplateService;
import com.ouroboros.model.AppInfo;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/app")
@SuppressWarnings("unused")
public class AppManagementController {
  private AppManagementService appManagementService;
  private AppCategoryService appCategoryService;
  private AppTemplateService appTemplateService;

  public AppManagementController(AppManagementService appManagementService, AppCategoryService appCategoryService, AppTemplateService appTemplateService) {
    this.appManagementService = appManagementService;
    this.appCategoryService = appCategoryService;
    this.appTemplateService = appTemplateService;
  }

  @GetMapping
  public ItemsWebApiResult<AppInfoWithMeta> listApps(@RequestParam(name = "category", required = false, defaultValue = "") String category) {
    var appList = appManagementService.listApps(category);
    return ItemsWebApiResult.of(appList);
  }

  @PostMapping
  public WebApiResult<Void> createApp(@RequestBody AppInfo app, @RequestParam(name = "category", required = false, defaultValue = "") String category) {
    var result = appManagementService.createApp(app);
    if (!category.trim().isEmpty()) {
      appCategoryService.addApp(category, app.getName());
    }
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @PutMapping("/{appName}")
  public WebApiResult<Void> updateApp(@PathVariable("appName") String appName, @RequestBody AppInfo app) {
    var result = appManagementService.updateApp(appName, app);
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @PostMapping("/{appName}/rename")
  public WebApiResult<Void> renameApp(@PathVariable("appName") String appName, @RequestBody RenameRequest renameRequest) {
    if (renameRequest.getNewName().trim().isEmpty()) {
      return WebApiResult.error("新名称不能为空");
    }
    var result = appManagementService.renameApp(appName, renameRequest.getNewName());
    if (result.isSuccess()) {
      appCategoryService.updateAppName(appName, renameRequest.getNewName());
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @PostMapping("/{appName}/move")
  public WebApiResult<Void> moveApp(@PathVariable("appName") String appName, @RequestBody MoveRequest moveRequest) {
    appCategoryService.moveApp(moveRequest.getCategory(), appName, moveRequest.getNewCategory());
    return WebApiResult.ok();
  }

  @DeleteMapping("/{appName}")
  public WebApiResult<Void> deleteApp(@PathVariable("appName") String appName) {
    var result = appManagementService.deleteApp(appName);
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return (WebApiResult<Void>) WebApiResult.of(result.getCause());
    }
  }

  @GetMapping("/{appName}/export")
  public ResponseEntity<Resource> exportApp(@PathVariable("appName") String appName,
                                            @RequestParam(name = "includeUncommitted", required = false, defaultValue = "true") boolean includeUncommitted,
                                            @RequestParam(name = "includeGitRepo", required = false, defaultValue = "true") boolean includeGitRepo) throws UnsupportedEncodingException {
    var result = appManagementService.exportApp(appName, includeUncommitted, includeGitRepo);
    if (result.isFailure()) {
      return ResponseEntity.badRequest().build();
    }

    var headers = new HttpHeaders();
    var resource = new InputStreamResource(result.get());
    headers.add(HttpHeaders.CONTENT_TYPE, "application/zip");
    headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + URLEncoder.encode(appName + ".zip", "UTF-8") + "\"");
    return ResponseEntity.ok()
        .headers(headers)
        .body(resource);
  }

  @PostMapping("/import")
  public WebApiResult<Void> importApp(@RequestParam("file") MultipartFile file) throws IOException {
    var result = appManagementService.importApp(file.getInputStream());
    if (result.isSuccess()) {
      return WebApiResult.ok();
    } else {
      return WebApiResult.error(result.getCause().getMessage());
    }
  }

  @PostMapping("/{appName}/create-template")
  public WebApiResult<Void> createTemplate(@PathVariable("appName") String appName, @RequestBody CreateTemplateFromAppRequest createTemplateFromAppRequest) {
    var template = new AppTemplateDTO();
    template.setName(createTemplateFromAppRequest.getName())
        .setTitle(createTemplateFromAppRequest.getTitle())
        .setIcon(createTemplateFromAppRequest.getIcon())
        .setDescription(createTemplateFromAppRequest.getDescription())
        .setVersion(createTemplateFromAppRequest.getVersion())
        .setAuthor(createTemplateFromAppRequest.getAuthor());
    var result = appTemplateService.createTemplateFromApp(template, appName);
    return WebApiResult.of(result);
  }

  static public class RenameRequest {
    public String newName;

    public String getNewName() {
      return newName;
    }

    public void setNewName(String newName) {
      this.newName = newName;
    }
  }

  static public class MoveRequest {
    String category;
    String newCategory;

    public String getCategory() {
      return category;
    }

    public void setCategory(String category) {
      this.category = category;
    }

    public String getNewCategory() {
      return newCategory;
    }

    public void setNewCategory(String newCategory) {
      this.newCategory = newCategory;
    }
  }

  static public class CreateTemplateFromAppRequest {
    String name;
    String title;
    String icon;
    String description;
    String version;
    String author;

    public String getName() {
      return name;
    }

    public CreateTemplateFromAppRequest setName(String name) {
      this.name = name;
      return this;
    }

    public String getTitle() {
      return title;
    }

    public CreateTemplateFromAppRequest setTitle(String title) {
      this.title = title;
      return this;
    }

    public String getIcon() {
      return icon;
    }

    public CreateTemplateFromAppRequest setIcon(String icon) {
      this.icon = icon;
      return this;
    }

    public String getDescription() {
      return description;
    }

    public CreateTemplateFromAppRequest setDescription(String description) {
      this.description = description;
      return this;
    }

    public String getVersion() {
      return version;
    }

    public CreateTemplateFromAppRequest setVersion(String version) {
      this.version = version;
      return this;
    }

    public String getAuthor() {
      return author;
    }

    public CreateTemplateFromAppRequest setAuthor(String author) {
      this.author = author;
      return this;
    }
  }
}
