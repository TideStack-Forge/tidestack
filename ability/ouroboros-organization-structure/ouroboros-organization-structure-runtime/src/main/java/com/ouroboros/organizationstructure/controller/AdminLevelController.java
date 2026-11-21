package com.ouroboros.organizationstructure.controller;

import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/admin-level")
public class AdminLevelController {

  private final AdminLevelService adminLevelService;

  public AdminLevelController(AdminLevelService adminLevelService) {
    this.adminLevelService = adminLevelService;
  }

  @Operation(summary = "查询行政级别列表", description = "按范围查询全部行政级别。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回行政级别列表")
  })
  @GetMapping
  public ItemsWebApiResult<AdminLevelEntity> list(
      @Parameter(description = "范围：ORG 或 DEPT")
      @RequestParam(name = "scope", required = false) String scope,
      @Parameter(description = "是否只返回启用项")
      @RequestParam(name = "enabled", required = false) Boolean enabled) throws Exception {
    List<AdminLevelEntity> items = Boolean.TRUE.equals(enabled)
        ? adminLevelService.findEnabledByScope(scope)
        : StringUtils.isBlank(scope) ? adminLevelService.findAll() : adminLevelService.findByScope(scope);
    return ItemsWebApiResult.success(items);
  }

  @Operation(summary = "获取行政级别详情", description = "根据 ID 获取行政级别详情。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回行政级别详情"),
      @ApiResponse(responseCode = "404", description = "行政级别不存在")
  })
  @GetMapping("/{id}")
  public WebApiResult<AdminLevelEntity> detail(
      @Parameter(description = "行政级别 ID", required = true)
      @PathVariable("id") String id) throws Exception {
    Optional<AdminLevelEntity> entity = adminLevelService.findById(id);
    return entity.map(WebApiResult::success).orElse(WebApiResult.error(404, "行政级别不存在"));
  }

  @Operation(summary = "创建行政级别", description = "新增一个行政级别。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "行政级别创建成功")
  })
  @PostMapping
  public WebApiResult<AdminLevelEntity> create(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "行政级别创建请求", required = true)
      @RequestBody AdminLevelEntity entity) throws Exception {
    return WebApiResult.success(adminLevelService.create(entity));
  }

  @Operation(summary = "更新行政级别", description = "根据 ID 更新行政级别。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "行政级别更新成功"),
      @ApiResponse(responseCode = "404", description = "行政级别不存在")
  })
  @PutMapping("/{id}")
  public WebApiResult<Boolean> update(
      @Parameter(description = "行政级别 ID", required = true)
      @PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "行政级别更新请求", required = true)
      @RequestBody AdminLevelEntity entity) throws Exception {
    return WebApiResult.success(adminLevelService.update(id, entity));
  }

  @Operation(summary = "删除行政级别", description = "根据 ID 删除行政级别。已引用的级别无法删除。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "行政级别删除成功"),
      @ApiResponse(responseCode = "404", description = "行政级别不存在"),
      @ApiResponse(responseCode = "409", description = "行政级别已被组织节点引用")
  })
  @DeleteMapping("/{id}")
  public WebApiResult<Boolean> delete(
      @Parameter(description = "行政级别 ID", required = true)
      @PathVariable("id") String id) throws Exception {
    return WebApiResult.success(adminLevelService.delete(id));
  }

  @Operation(summary = "禁用行政级别", description = "根据 ID 禁用行政级别。", tags = {"organization"})
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "行政级别禁用成功"),
      @ApiResponse(responseCode = "404", description = "行政级别不存在")
  })
  @PostMapping("/{id}/disable")
  public WebApiResult<Boolean> disable(
      @Parameter(description = "行政级别 ID", required = true)
      @PathVariable("id") String id) throws Exception {
    return WebApiResult.success(adminLevelService.disable(id));
  }
}
