package com.ouroboros.logicflow.manage.web;

import java.util.Map;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.manage.service.FlowObservationService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/runtime/logicflows/models/{name}")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "策略、时间范围或命令无效"),
    @ApiResponse(responseCode = "401", description = "未认证"),
    @ApiResponse(responseCode = "403", description = "缺少模型读取或观测权限"),
    @ApiResponse(responseCode = "404", description = "流程或样本不存在"),
    @ApiResponse(responseCode = "409", description = "配置修订冲突，保留草稿重新核对"),
    @ApiResponse(responseCode = "503", description = "配置、存储或运行时不可用")
})
public class FlowObservationController {
  private final FlowObservationService service;
  private final RuntimeFlowAccess access;
  public FlowObservationController(FlowObservationService service, RuntimeFlowAccess access) { this.service = service; this.access = access; }
  @ModelAttribute public void noCache(HttpServletResponse response) { response.setHeader("Cache-Control", "no-store"); }
  @GetMapping("/observation-policy")
  @Operation(operationId = "runtimeLogicFlowObservationPolicy", summary = "读取本流程的期望采集策略，未配置默认关闭")
  public WebApiResult<PolicyView> policy(@PathVariable String name) { access.require(RuntimeFlowAccess.READ); return WebApiResult.ok(service.read(name)); }
  @PutMapping("/observation-policy")
  @Operation(operationId = "runtimeLogicFlowObservationPolicySave", summary = "按配置修订条件修改本流程采集策略")
  public WebApiResult<PolicyView> save(@PathVariable String name, @RequestBody Command command) {
    access.require(RuntimeFlowAccess.OBSERVE_MANAGE); return WebApiResult.ok(service.save(name, command));
  }
  @Operation(operationId = "runtimeLogicFlowObservationStatus", summary = "核对期望配置修订与各启动实例的采用回报")
  @GetMapping("/observation-status")
  public WebApiResult<Statuses> statuses(@PathVariable String name) {
    access.require(RuntimeFlowAccess.READ);
    var policy = service.read(name);
    return WebApiResult.ok(new Statuses(policy.name(), policy.revision(), service.statuses(name)));
  }
  @GetMapping("/metrics")
  @Operation(operationId = "runtimeLogicFlowMetrics", summary = "按时间窗、启动实例与运行版本查询已采集统计，P95 合并直方图计算")
  public WebApiResult<Metrics> metrics(@PathVariable String name, @RequestParam(defaultValue = "15") int minutes,
      @RequestParam(required = false) String bootId, @RequestParam(required = false) String runtimeRevision) {
    access.require(RuntimeFlowAccess.OBSERVE_READ); return WebApiResult.ok(service.metrics(name, minutes, bootId, runtimeRevision));
  }
  @Operation(operationId = "runtimeLogicFlowDiagnosticRuns", summary = "读取本流程最近一百条脱敏诊断样本")
  @GetMapping("/diagnostic-runs")
  public WebApiResult<Map<String, Object>> runs(@PathVariable String name) {
    access.require(RuntimeFlowAccess.OBSERVE_READ); return WebApiResult.ok(Map.of("items", service.runs(name)));
  }
  @Operation(operationId = "runtimeLogicFlowDiagnosticRun", summary = "读取属于本流程的单次诊断样本与节点时序")
  @GetMapping("/diagnostic-runs/{id}")
  public WebApiResult<Run> run(@PathVariable String name, @PathVariable String id) {
    access.require(RuntimeFlowAccess.OBSERVE_READ); return WebApiResult.ok(service.run(name, id));
  }
  @Operation(operationId = "runtimeLogicFlowObservationChanges", summary = "读取本流程采集策略操作及到期审计")
  @GetMapping("/observation-changes")
  public WebApiResult<Map<String, Object>> history(@PathVariable String name) {
    access.require(RuntimeFlowAccess.OBSERVE_READ); return WebApiResult.ok(Map.of("items", service.history(name)));
  }
}
