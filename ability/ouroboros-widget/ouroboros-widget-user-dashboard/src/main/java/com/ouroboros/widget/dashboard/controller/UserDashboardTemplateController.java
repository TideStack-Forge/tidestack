package com.ouroboros.widget.dashboard.controller;

import java.util.Map;
import java.util.stream.Collectors;

import jakarta.annotation.security.PermitAll;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.SelectOption;
import com.ouroboros.web.WebApiResult;
import com.ouroboros.widget.Dashboard;
import com.ouroboros.widget.dashboard.model.UserDashboardTemplateModel;
import com.ouroboros.widget.dashboard.service.UserDashboardTemplateService;

/**
 * 用户仪表盘模板控制器
 * 处理用户仪表盘模板相关的HTTP请求
 */
@RestController
@RequestMapping("/api/user-dashboard-template")
public class UserDashboardTemplateController {
  // 用户仪表盘模板服务
  private UserDashboardTemplateService userDashboardTemplateService;

  /**
   * 构造函数，通过依赖注入方式初始化服务
   * @param userDashboardTemplateService 用户仪表盘模板服务
   */
  public UserDashboardTemplateController(UserDashboardTemplateService userDashboardTemplateService) {
    this.userDashboardTemplateService = userDashboardTemplateService;
  }

  /**
   * 获取所有模板选项
   * @return 返回包含所有模板选项的ItemsWebApiResult
   */
  @Operation(summary = "列出仪表盘模板选项", description = "返回仪表盘模板下拉选项列表。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回仪表盘模板选项")
  })
  @GetMapping("/select")
  @PermitAll
  public ItemsWebApiResult<SelectOption> select() {
    var optionss = userDashboardTemplateService.getAllTemplates().stream()
        .map(template -> new SelectOption(template.getId(), template.getName()).addExtraProp("description", template.getDescription()))
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(optionss);
  }

  /**
   * 根据ID获取模板
   * @param id 模板ID
   * @return 返回模板信息的WebApiResult
   */
  @Operation(summary = "获取仪表盘模板详情", description = "根据模板 ID 获取仪表盘模板定义。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回仪表盘模板详情"),
      @ApiResponse(responseCode = "404", description = "仪表盘模板不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<UserDashboardTemplateModel> get(
      @Parameter(description = "仪表盘模板 ID", required = true)
      @PathVariable("id") String id) {
    return WebApiResult.of(404, "仪表盘模板不存在", userDashboardTemplateService.getTemplate(id));
  }

  /**
   * 加载模板
   * @param id 模板ID
   * @return 模板数据
   */
  @Operation(summary = "加载仪表盘模板内容", description = "根据模板 ID 加载仪表盘模板数据。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回仪表盘模板内容"),
      @ApiResponse(responseCode = "404", description = "仪表盘模板不存在")
  })
  @GetMapping("/{id}/load")
  @PermitAll
  public WebApiResult<Map<String, Object>> load(
      @Parameter(description = "仪表盘模板 ID", required = true)
      @PathVariable("id") String id) {
    var template = userDashboardTemplateService.getTemplate(id);
    return WebApiResult.of(404, "仪表盘模板不存在", template.map(UserDashboardTemplateModel::getTemplate));
  }

  /**
   * 获取所有模板列表
   * @return 返回包含所有模板的ItemsWebApiResult
   */
  @Operation(summary = "列出仪表盘模板", description = "返回全部仪表盘模板列表。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回仪表盘模板列表")
  })
  @GetMapping
  public ItemsWebApiResult<UserDashboardTemplateModel> list() {
    var templates = userDashboardTemplateService.getAllTemplates();
    return ItemsWebApiResult.ok(templates);
  }

  /**
   * 添加新模板
   * @param template 模板模型
   * @return 返回添加后的模板信息
   */
  @Operation(summary = "创建仪表盘模板", description = "新增一个仪表盘模板定义。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "仪表盘模板创建成功"),
      @ApiResponse(responseCode = "400", description = "仪表盘模板参数不合法")
  })
  @PostMapping
  public WebApiResult<UserDashboardTemplateModel> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "仪表盘模板定义", required = true)
      @RequestBody UserDashboardTemplateModel template) {
    var result = userDashboardTemplateService.addTemplate(template);
    return WebApiResult.of(result);
  }

  /**
   * 更新模板信息
   * @param id 模板ID
   * @param template 模板模型
   * @return 返回更新后的模板信息
   */
  @Operation(summary = "更新仪表盘模板", description = "根据模板 ID 更新仪表盘模板定义。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "仪表盘模板更新成功"),
      @ApiResponse(responseCode = "404", description = "仪表盘模板不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<UserDashboardTemplateModel> update(
      @Parameter(description = "仪表盘模板 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "更新后的仪表盘模板定义", required = true)
      @RequestBody UserDashboardTemplateModel template) {
    if (!userDashboardTemplateService.getTemplate(id).isPresent()) {
      return notFound();
    }
    var result = userDashboardTemplateService.updateTemplate(id, template);
    return WebApiResult.of(result);
  }

  /**
   * 删除模板
   * @param id 模板ID
   * @return 返回操作结果的WebApiResult
   */
  @Operation(summary = "删除仪表盘模板", description = "根据模板 ID 删除仪表盘模板。", tags = {"widget"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "仪表盘模板删除成功"),
      @ApiResponse(responseCode = "404", description = "仪表盘模板不存在")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Void> delete(
      @Parameter(description = "仪表盘模板 ID", required = true)
      @PathVariable("id") String id) {
    if (!userDashboardTemplateService.getTemplate(id).isPresent()) {
      return notFound();
    }
    return WebApiResult.of(userDashboardTemplateService.deleteTemplate(id));
  }

  private <T> WebApiResult<T> notFound() {
    return WebApiResult.error(404, "仪表盘模板不存在");
  }
}
