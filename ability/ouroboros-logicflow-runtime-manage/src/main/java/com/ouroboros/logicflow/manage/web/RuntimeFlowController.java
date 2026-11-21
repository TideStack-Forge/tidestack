package com.ouroboros.logicflow.manage.web;

import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.*;
import com.ouroboros.logicflow.manage.service.RuntimeFlowService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/runtime/logicflows")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "命令、分页或公开流程合同不合法"),
    @ApiResponse(responseCode = "401", description = "未认证"),
    @ApiResponse(responseCode = "403", description = "缺少 read、edit 或 reload 权限"),
    @ApiResponse(responseCode = "404", description = "模型或操作不存在"),
    @ApiResponse(responseCode = "409", description = "版本冲突、只读来源或 operationId 冲突；保留草稿重新对比"),
    @ApiResponse(responseCode = "422", description = "候选或受影响父流程检查未通过"),
    @ApiResponse(responseCode = "503", description = "存储、Context 或独立刷新能力未就绪")
})
public class RuntimeFlowController {
  private final RuntimeFlowService service;
  private final RuntimeFlowStore store;
  private final RuntimeFlowAccess access;

  public RuntimeFlowController(RuntimeFlowService service, RuntimeFlowStore store, RuntimeFlowAccess access) {
    this.service = service; this.store = store; this.access = access;
  }

  @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }

  @Operation(operationId = "runtimeLogicFlowList", summary = "查询已部署逻辑流及运行时覆盖", description = "需要 systemSetting.logicflow.read；先搜索与筛选再分页，perPage 为 1–100。")
  @GetMapping("/models")
  public WebApiResult<Page> list(@RequestParam(defaultValue = "") String keyword,
      @RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int perPage) {
    return WebApiResult.ok(service.list(keyword, filter, page, perPage, access.require(RuntimeFlowAccess.READ)));
  }

  @Operation(operationId = "runtimeLogicFlowRead", summary = "观察默认、持久覆盖和当前实例完成态", description = "需要 read；editToken 绑定集合、名称、默认及实际发布来源，operationId 为新的规范 UUID。")
  @GetMapping("/models/{name}")
  public WebApiResult<View> read(@PathVariable String name) { return WebApiResult.ok(service.read(name, access.require(RuntimeFlowAccess.READ))); }

  @Operation(operationId = "runtimeLogicFlowOperationsView", summary = "读取本应用流程运维视图及最近一百条变更")
  @GetMapping("/models/{name}/operations-view")
  public WebApiResult<Operations> operations(@PathVariable String name) {
    return WebApiResult.ok(service.operations(name, access.require(RuntimeFlowAccess.READ)));
  }

  @Operation(operationId = "runtimeLogicFlowValidate", summary = "检查候选、差异和父子流影响范围，不执行流程", description = "需要 read + edit；HTTP 200 中 valid=false 表示检查不通过，检查不持久化也不执行流程。")
  @PostMapping("/models/{name}/validate")
  public WebApiResult<Validation> validate(@PathVariable String name, @RequestBody ValidationCommand command) {
    return WebApiResult.ok(service.validate(name, command, access.require(RuntimeFlowAccess.EDIT)));
  }

  @Operation(operationId = "runtimeLogicFlowSaveOverride", summary = "条件保存完整覆盖", description = "需要 read + edit；相同 actor、operationId 和请求可重放。202 只表示已提交，按实例 appliedVersion 核对。")
  @ApiResponses({@ApiResponse(responseCode = "202", description = "覆盖已提交，异步应用"), @ApiResponse(responseCode = "200", description = "内容无变化，操作已记录")})
  @PutMapping("/models/{name}/override")
  public WebApiResult<Change> save(@PathVariable String name, @RequestBody Command command, HttpServletResponse response) {
    return committed(service.save(name, command, access.require(RuntimeFlowAccess.EDIT)), response);
  }

  @Operation(operationId = "runtimeLogicFlowRevokeOverride", summary = "条件撤销覆盖", description = "需要 read + edit；恢复最后成功 Context 的部署默认，保留名称墓碑修订。")
  @ApiResponses({@ApiResponse(responseCode = "202", description = "撤销已提交"), @ApiResponse(responseCode = "200", description = "已经无活动覆盖")})
  @PostMapping("/models/{name}/revoke")
  public WebApiResult<Change> revoke(@PathVariable String name, @RequestBody VersionedCommand command, HttpServletResponse response) {
    return committed(service.revoke(name, command, access.require(RuntimeFlowAccess.EDIT)), response);
  }

  @Operation(operationId = "runtimeLogicFlowRestorePrevious", summary = "从同一模型的操作前态恢复为新修订", description = "需要 read + edit；fromChangeId 必须属于同一模型，恢复仍执行当前版本与候选检查。")
  @ApiResponses({@ApiResponse(responseCode = "202", description = "前态恢复已提交"), @ApiResponse(responseCode = "200", description = "内容无变化")})
  @PostMapping("/models/{name}/restore-previous")
  public WebApiResult<Change> restore(@PathVariable String name, @RequestBody RestoreCommand command, HttpServletResponse response) {
    return committed(service.restore(name, command, access.require(RuntimeFlowAccess.EDIT)), response);
  }

  @Operation(operationId = "runtimeLogicFlowOperation", summary = "读取操作回执及实例状态", description = "需要 read；superseded 表示已有更新的持久集合，不能把历史回执当作当前目标。")
  @GetMapping("/operations/{id}")
  public WebApiResult<com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.Operation> operation(@PathVariable String id) {
    return WebApiResult.ok(service.operation(id, access.require(RuntimeFlowAccess.READ)));
  }

  @Operation(operationId = "runtimeLogicFlowInstances", summary = "观察各启动实例的目标与应用代次", description = "需要 read；reportedAt 是回报时间，历史 bootId 不等于在线集群成员。")
  @GetMapping("/instances")
  public WebApiResult<List<RuntimeFlowStore.Instance>> instances() {
    access.require(RuntimeFlowAccess.READ);
    return WebApiResult.ok(store.instances());
  }

  @Operation(operationId = "runtimeLogicFlowApply", summary = "提示当前实例核对最新持久代次", description = "需要 read + reload；expectedCatalogVersion 必须匹配当前集合，不触发完整 Context。")
  @ApiResponse(responseCode = "202", description = "当前实例已收到核对提示；后续观察实例状态")
  @PostMapping("/apply")
  public WebApiResult<ApplyReceipt> apply(@RequestBody ApplyRequest request, HttpServletResponse response) {
    var result = service.requestApply(request.expectedCatalogVersion(), access.require(RuntimeFlowAccess.RELOAD));
    response.setStatus(202);
    return WebApiResult.ok(result);
  }

  private WebApiResult<Change> committed(Change change, HttpServletResponse response) {
    response.setStatus(change.changed() ? 202 : 200);
    return WebApiResult.build(0, change.changed() ? "覆盖已提交，正在核对各实例应用状态" : "无内容变化，操作已记录", change);
  }
}
