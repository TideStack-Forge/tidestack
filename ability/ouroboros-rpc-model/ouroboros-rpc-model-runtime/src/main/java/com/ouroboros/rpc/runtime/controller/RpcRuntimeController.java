package com.ouroboros.rpc.runtime.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.rpc.execution.RpcInvocationRequest;
import com.ouroboros.rpc.execution.RpcInvocationResult;
import com.ouroboros.rpc.runtime.model.RpcRuntimePolicyModel;
import com.ouroboros.rpc.runtime.model.RpcRuntimeStatusModel;
import com.ouroboros.rpc.runtime.service.RpcRuntimeOperationService;
import com.ouroboros.rpc.runtime.service.RpcRuntimePolicyService;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryItem;
import com.ouroboros.rpc.runtime.service.RpcRuntimeRegistryService;
import com.ouroboros.util.JsonBag;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/rpc-runtime")
public class RpcRuntimeController {
  private final RpcRuntimeRegistryService registryService;
  private final RpcRuntimePolicyService policyService;
  private final RpcRuntimeOperationService operationService;

  public RpcRuntimeController(
      RpcRuntimeRegistryService registryService,
      RpcRuntimePolicyService policyService,
      RpcRuntimeOperationService operationService
  ) {
    this.registryService = registryService;
    this.policyService = policyService;
    this.operationService = operationService;
  }

  @Operation(
      summary = "列出 RPC 运行时模型",
      description = "返回当前已注册的 RPC 运行时模型列表及其策略状态。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回运行时模型列表")
  })
  @GetMapping
  public ItemsWebApiResult<RpcRuntimeModelVO> list() {
    List<RpcRuntimeModelVO> items = registryService.listModels().stream()
        .map(RpcRuntimeModelVO::new)
        .collect(Collectors.toList());
    return ItemsWebApiResult.ok(items);
  }

  @Operation(
      summary = "获取 RPC 运行时模型详情",
      description = "根据模型名称查询单个 RPC 运行时模型详情。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回模型详情"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @GetMapping("/{modelName}")
  public WebApiResult<RpcRuntimeDetailVO> get(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName) {
    return WebApiResult.of(404, "指定模型不存在", findModel(modelName).map(RpcRuntimeDetailVO::new));
  }

  @Operation(
      summary = "更新 RPC 运行时策略",
      description = "根据模型名称更新 RPC 运行时策略配置。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "策略更新成功"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @PutMapping("/{modelName}/policy")
  public WebApiResult<RpcRuntimePolicyModel> updatePolicy(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "运行时策略配置", required = true)
      @RequestBody RpcRuntimePolicyDTO policy
  ) {
    if (!findModel(modelName).isPresent()) {
      return notFound();
    }
    return WebApiResult.ok(policyService.updatePolicy(modelName, policy == null ? new RpcRuntimePolicyModel() : policy.toModel()));
  }

  @Operation(
      summary = "执行 RPC 健康检查",
      description = "对指定 RPC 运行时模型执行一次健康检查。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "健康检查执行成功"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @PostMapping("/{modelName}/actions/health-check")
  public WebApiResult<RpcRuntimeInvocationResultVO> healthCheck(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName) {
    if (!findModel(modelName).isPresent()) {
      return notFound();
    }
    return WebApiResult.ok(new RpcRuntimeInvocationResultVO(modelName, operationService.healthCheck(modelName)));
  }

  @Operation(
      summary = "重置 RPC 运行时状态",
      description = "重置指定 RPC 运行时模型的健康状态和故障统计。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "运行时状态重置成功"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @PostMapping("/{modelName}/actions/reset-status")
  public WebApiResult<RpcRuntimeStatusModel> resetStatus(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName) {
    if (!findModel(modelName).isPresent()) {
      return notFound();
    }
    return WebApiResult.ok(operationService.resetStatus(modelName));
  }

  @Operation(
      summary = "修复 RPC 运行时策略",
      description = "根据当前模型定义重建指定 RPC 运行时模型的策略配置。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "策略修复成功"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @PostMapping("/{modelName}/actions/repair-policy")
  public WebApiResult<RpcRuntimePolicyModel> repairPolicy(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName) {
    if (!findModel(modelName).isPresent()) {
      return notFound();
    }
    return WebApiResult.ok(operationService.repairPolicy(modelName));
  }

  @Operation(
      summary = "测试调用 RPC 模型",
      description = "使用手工输入参数对指定 RPC 运行时模型执行一次测试调用。",
      tags = {"rpc"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "测试调用执行成功"),
      @ApiResponse(responseCode = "404", description = "指定模型不存在")
  })
  @PostMapping("/{modelName}/actions/test-invoke")
  public WebApiResult<RpcRuntimeInvocationResultVO> testInvoke(
      @Parameter(description = "RPC 运行时模型名称", required = true)
      @PathVariable("modelName") String modelName,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "测试调用参数")
      @RequestBody(required = false) RpcRuntimeTestInvokeRequest request
  ) {
    if (!findModel(modelName).isPresent()) {
      return notFound();
    }

    Map<String, Object> parameters = request == null || request.getParameters() == null
        ? Collections.emptyMap()
        : request.getParameters();
    RpcInvocationRequest invocationRequest = new RpcInvocationRequest(modelName, parameters, false, "manual-test", Collections.emptyMap());
    RpcInvocationResult result = operationService.testInvoke(invocationRequest);
    return WebApiResult.ok(new RpcRuntimeInvocationResultVO(modelName, result));
  }

  private <T> WebApiResult<T> notFound() {
    return WebApiResult.error(404, "指定模型不存在");
  }

  private Optional<RpcRuntimeRegistryItem> findModel(String modelName) {
    return registryService.getModel(modelName);
  }

  public static class RpcRuntimeModelVO {
    private final RpcRuntimeRegistryItem item;

    public RpcRuntimeModelVO(RpcRuntimeRegistryItem item) {
      this.item = item;
    }

    public String getModelName() {
      return item.getModelName();
    }

    public String getTitle() {
      return item.getTitle();
    }

    public String getGroup() {
      return item.getGroup();
    }

    public String getType() {
      return item.getType();
    }

    public String getAuthCredential() {
      return item.getAuthCredential();
    }

    public Map<String, Object> getDefinitionExtraProps() {
      return item.getDefinitionExtraProps();
    }

    public RpcRuntimePolicyModel getPolicy() {
      return item.getPolicy();
    }

    public RpcRuntimeStatusModel getStatus() {
      return item.getStatus();
    }
  }

  public static class RpcRuntimeDetailVO extends RpcRuntimeModelVO {
    public RpcRuntimeDetailVO(RpcRuntimeRegistryItem item) {
      super(item);
    }
  }

  public static class RpcRuntimePolicyDTO {
    private Boolean enabled;
    private Boolean loggingEnabled;
    private Boolean logRequestBody;
    private Boolean logResponseBody;
    private Object maskHeaders;
    private Object maskFields;
    private Boolean metricsEnabled;
    private Boolean availabilityEnabled;
    private String forcedAvailabilityStatus;
    private Long timeoutOverrideMs;
    private Integer retryOverride;

    public RpcRuntimePolicyModel toModel() {
      RpcRuntimePolicyModel model = new RpcRuntimePolicyModel();
      model.setEnabled(enabled);
      model.setLoggingEnabled(loggingEnabled);
      model.setLogRequestBody(logRequestBody);
      model.setLogResponseBody(logResponseBody);
      model.setMaskHeaders(maskHeaders == null ? null : JsonBag.of(maskHeaders));
      model.setMaskFields(maskFields == null ? null : JsonBag.of(maskFields));
      model.setMetricsEnabled(metricsEnabled);
      model.setAvailabilityEnabled(availabilityEnabled);
      model.setForcedAvailabilityStatus(forcedAvailabilityStatus);
      model.setTimeoutOverrideMs(timeoutOverrideMs);
      model.setRetryOverride(retryOverride);
      return model;
    }

    public Boolean getEnabled() {
      return enabled;
    }

    public void setEnabled(Boolean enabled) {
      this.enabled = enabled;
    }

    public Boolean getLoggingEnabled() {
      return loggingEnabled;
    }

    public void setLoggingEnabled(Boolean loggingEnabled) {
      this.loggingEnabled = loggingEnabled;
    }

    public Boolean getLogRequestBody() {
      return logRequestBody;
    }

    public void setLogRequestBody(Boolean logRequestBody) {
      this.logRequestBody = logRequestBody;
    }

    public Boolean getLogResponseBody() {
      return logResponseBody;
    }

    public void setLogResponseBody(Boolean logResponseBody) {
      this.logResponseBody = logResponseBody;
    }

    public Object getMaskHeaders() {
      return maskHeaders;
    }

    public void setMaskHeaders(Object maskHeaders) {
      this.maskHeaders = maskHeaders;
    }

    public Object getMaskFields() {
      return maskFields;
    }

    public void setMaskFields(Object maskFields) {
      this.maskFields = maskFields;
    }

    public Boolean getMetricsEnabled() {
      return metricsEnabled;
    }

    public void setMetricsEnabled(Boolean metricsEnabled) {
      this.metricsEnabled = metricsEnabled;
    }

    public Boolean getAvailabilityEnabled() {
      return availabilityEnabled;
    }

    public void setAvailabilityEnabled(Boolean availabilityEnabled) {
      this.availabilityEnabled = availabilityEnabled;
    }

    public String getForcedAvailabilityStatus() {
      return forcedAvailabilityStatus;
    }

    public void setForcedAvailabilityStatus(String forcedAvailabilityStatus) {
      this.forcedAvailabilityStatus = forcedAvailabilityStatus;
    }

    public Long getTimeoutOverrideMs() {
      return timeoutOverrideMs;
    }

    public void setTimeoutOverrideMs(Long timeoutOverrideMs) {
      this.timeoutOverrideMs = timeoutOverrideMs;
    }

    public Integer getRetryOverride() {
      return retryOverride;
    }

    public void setRetryOverride(Integer retryOverride) {
      this.retryOverride = retryOverride;
    }
  }

  public static class RpcRuntimeTestInvokeRequest {
    private Map<String, Object> parameters;

    public Map<String, Object> getParameters() {
      return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
      this.parameters = parameters;
    }
  }

  public static class RpcRuntimeInvocationResultVO {
    private final String modelName;
    private final boolean success;
    private final Object payload;
    private final String errorMessage;
    private final long durationMs;

    public RpcRuntimeInvocationResultVO(String modelName, RpcInvocationResult result) {
      this.modelName = modelName;
      this.success = result != null && result.isSuccess();
      this.payload = result == null ? null : result.getPayload();
      this.errorMessage = result == null || result.getError() == null ? null : result.getError().getMessage();
      this.durationMs = result == null ? 0L : result.getDurationMs();
    }

    public String getModelName() {
      return modelName;
    }

    public boolean isSuccess() {
      return success;
    }

    public Object getPayload() {
      return payload;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public long getDurationMs() {
      return durationMs;
    }
  }
}
