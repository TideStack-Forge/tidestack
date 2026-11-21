package com.ouroboros.notification.controller;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.service.NotificationTemplateRuntimeManagementService;
import com.ouroboros.notification.service.NotificationTemplateService;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.*;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.web.PagedResult;
import com.ouroboros.web.PagedWebApiResult;
import com.ouroboros.web.WebApiResult;

/** Runtime commands share one use case, including the retained ID routes. */
@RestController
@RequestMapping("/api/sys/notification-template")
public class NotificationTemplateController {
  @Autowired
  private NotificationTemplateService service;
  @Autowired
  private NotificationTemplateRuntimeManagementService management;

  @ModelAttribute
  public void preventCaching(HttpServletResponse response) {
    response.setHeader("Cache-Control", "no-store");
  }

  @Operation(summary = "创建通知模板覆盖", description = "仅覆盖部署模板；必须提供预期版本与 changes。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "通知模板覆盖已保存"),
      @ApiResponse(responseCode = "400", description = "通知模板参数不合法"), @ApiResponse(responseCode = "409", description = "模板版本冲突")})
  @PostMapping
  public WebApiResult<WriteResult> add(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "模板名称、预期版本和修改字段", required = true)
      @RequestBody CreateOverride request) {
    if (request.name() == null || request.name().isBlank()) { throw new BusinessException(400, "模板名称不能为空"); }
    return write(management.saveRuntimeOverride(request.name(), request.command(), actor()));
  }

  @Operation(summary = "撤销通知模板覆盖", description = "按覆盖 ID 撤销，必须提供预期版本。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "覆盖已撤销"), @ApiResponse(responseCode = "404", description = "通知模板不存在")})
  @DeleteMapping("/{id}")
  public WebApiResult<WriteResult> delete(@PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "预期版本和修改字段", required = true) @RequestBody Command command) {
    return write(management.restoreDefault(nameForId(id), command, actor()));
  }

  @Operation(summary = "更新通知模板覆盖", description = "按覆盖 ID 应用 changes，必须提供预期版本。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "通知模板覆盖已保存"), @ApiResponse(responseCode = "400", description = "通知模板参数不合法"),
      @ApiResponse(responseCode = "404", description = "通知模板不存在"), @ApiResponse(responseCode = "409", description = "模板版本冲突")})
  @PutMapping("/{id}")
  public WebApiResult<WriteResult> update(@PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "预期版本和修改字段", required = true) @RequestBody Command command) {
    return write(management.saveRuntimeOverride(nameForId(id), command, actor()));
  }

  @Operation(summary = "部分更新通知模板覆盖", description = "复用同一版本校验与覆盖保存用例。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "通知模板覆盖已保存"), @ApiResponse(responseCode = "400", description = "通知模板参数不合法"),
      @ApiResponse(responseCode = "404", description = "通知模板不存在"), @ApiResponse(responseCode = "409", description = "模板版本冲突")})
  @PatchMapping("/{id}")
  public WebApiResult<WriteResult> partialUpdate(@PathVariable("id") String id,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "预期版本和修改字段", required = true) @RequestBody Command command) { return update(id, command); }

  @Operation(summary = "获取通知模板详情", description = "按模板名称查询当前有效定义。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "成功返回通知模板详情"), @ApiResponse(responseCode = "404", description = "通知模板不存在")})
  @GetMapping("/{name}")
  public WebApiResult<Map<String, Object>> get(@Parameter(description = "通知模板名称", required = true) @PathVariable("name") String name) { return WebApiResult.ok(summary(management.getRuntimeDefinition(name))); }

  @Operation(summary = "获取通知模板运行时定义", tags = {"notification"})
  @GetMapping("/{name}/runtime-definition")
  public WebApiResult<Definition> runtimeDefinition(@PathVariable("name") String name) { return WebApiResult.ok(management.getRuntimeDefinition(name)); }

  @Operation(summary = "检查通知模板覆盖", description = "只构造候选并检查，无持久化或发送副作用。", tags = {"notification"})
  @PostMapping("/{name}/runtime-override/validate")
  public WebApiResult<Validation> validate(@PathVariable("name") String name, @RequestBody Command command) {
    return WebApiResult.ok(management.validateRuntimeOverride(name, command));
  }

  @Operation(summary = "保存通知模板覆盖", description = "条件保存完整覆盖，返回提交状态及版本。", tags = {"notification"})
  @PutMapping("/{name}/runtime-override")
  public WebApiResult<WriteResult> save(@PathVariable("name") String name, @RequestBody Command command) {
    return write(management.saveRuntimeOverride(name, command, actor()));
  }

  @Operation(summary = "恢复部署默认通知模板", tags = {"notification"})
  @PostMapping("/{name}/restore-default")
  public WebApiResult<WriteResult> restore(@PathVariable("name") String name, @RequestBody Command command) {
    return write(management.restoreDefault(name, command, actor()));
  }

  @Operation(summary = "分页查询通知模板", description = "旧条件与来源、关键字、基线变化条件按 AND 组合，先筛选再分页。", tags = {"notification"})
  @ApiResponses({@ApiResponse(responseCode = "200", description = "成功返回通知模板分页结果")})
  @GetMapping
  public PagedWebApiResult<?> search(
      @Parameter(description = "模板名称关键字") @RequestParam(name = "name", required = false) String name,
      @Parameter(description = "模板标题关键字") @RequestParam(name = "title", required = false) String title,
      @RequestParam(name = "enable", required = false) Boolean enable,
      @RequestParam(name = "controlled", required = false) Boolean controlled,
      @Parameter(description = "页码") @RequestParam(name = "page", defaultValue = "1") Integer page,
      @Parameter(description = "每页条数") @RequestParam(name = "perPage", defaultValue = "20") Integer perPage,
      @RequestParam(name = "keyword", required = false) String keyword,
      @RequestParam(name = "source", required = false) String source,
      @RequestParam(name = "baseChanged", required = false) Boolean baseChanged) {
    if (source != null && !List.of("DEFAULT", "OVERRIDE", "DATABASE_ONLY").contains(source)) { throw new BusinessException(400, "来源筛选不合法"); }
    var definitions = management.getRuntimeDefinitions().stream()
        .filter(item -> contains(item.name(), name))
        .filter(item -> contains(item.effectiveTemplate().get("title"), title))
        .filter(item -> enable == null || enable.equals(item.effectiveTemplate().get("isEnabled")))
        .filter(item -> controlled == null || item.hasOverride() == controlled)
        .filter(item -> keyword == null || contains(item.name(), keyword) || contains(item.effectiveTemplate().get("title"), keyword))
        .filter(item -> source == null || source.equals(item.effectiveSource()))
        .filter(item -> baseChanged == null || baseChanged.equals(item.baseChanged()))
        .map(this::listSummary).toList();
    return PagedWebApiResult.ok(buildPagedResult(definitions.size(), definitions, page, perPage));
  }

  @ExceptionHandler(ManagementException.class)
  public WebApiResult<ErrorDetails> managementFailure(ManagementException failure) {
    return WebApiResult.build(failure.getErrorCode(), failure.getMessage(), failure.getDetails());
  }

  private WebApiResult<WriteResult> write(WriteResult result) {
    return result.committed() && !result.confirmed()
        ? WebApiResult.build(503, "已保存，生效状态待核对；请重新读取，不要重复提交", result) : WebApiResult.ok(result);
  }

  private String nameForId(String id) {
    var template = service.getByPrimaryKey(id);
    if (template == null) { throw new BusinessException(404, "通知模板不存在"); }
    return template.getName();
  }

  private String actor() { return CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity(); }

  private boolean contains(Object value, String keyword) { return keyword == null || value instanceof String text && text.contains(keyword); }

  private Map<String, Object> summary(Definition definition) {
    var result = new LinkedHashMap<>(definition.effectiveTemplate());
    result.put("isControlled", definition.hasOverride()); result.put("hasOverride", definition.hasOverride());
    result.put("defaultExists", definition.defaultExists()); result.put("effectiveSource", definition.effectiveSource());
    result.put("baseChanged", definition.baseChanged()); result.put("modifiedBy", definition.modifiedBy()); result.put("modifiedAt", definition.modifiedAt());
    result.put("availableActions", definition.availableActions()); result.put("diagnostics", definition.diagnostics());
    result.put("defaultRevision", definition.defaultRevision()); result.put("overrideRevision", definition.overrideRevision());
    return result;
  }

  private Map<String, Object> listSummary(Definition definition) {
    var result = summary(definition);
    result.remove("payloadSchema"); result.remove("channel");
    result.put("channels", definition.effectiveTemplate().get("channel") instanceof Map<?, ?> channels
        ? channels.keySet().stream().map(String::valueOf).sorted().toList() : List.of());
    result.put("channelLabels", definition.effectiveTemplate().get("channel") instanceof Map<?, ?> channels
        ? channels.keySet().stream().map(String::valueOf).sorted().map(id -> NotificationAdapterCenter.findSupplier(id)
            .flatMap(supplier -> supplier.getManagement()).map(description -> description.title()).orElse(id)).toList() : List.of());
    return result;
  }

  protected <T> PagedResult<T> buildPagedResult(long total, List<T> list, int page, int limit) {
    if (limit <= 0) { return PagedResult.of(total, list); }
    int start = (int) Math.min((long) (Math.max(page, 1) - 1) * limit, list.size());
    int end = (int) Math.min((long) start + limit, list.size());
    return PagedResult.of(total, list.subList(start, end));
  }

  public record CreateOverride(String name, String expectedDefaultRevision, String expectedOverrideRevision, Map<String, Object> changes) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectUnknownField(String field, Object value) { throw new BusinessException(400, "不支持的请求字段：" + field); }
    public Command command() { return new Command(expectedDefaultRevision, expectedOverrideRevision, changes); }
  }
}
