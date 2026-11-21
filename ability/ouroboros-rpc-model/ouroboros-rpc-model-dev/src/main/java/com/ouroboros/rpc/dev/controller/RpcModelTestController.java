package com.ouroboros.rpc.dev.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.rpc.dev.service.testing.RpcModelTestInit;
import com.ouroboros.rpc.dev.service.testing.RpcModelTestInitRequest;
import com.ouroboros.rpc.dev.service.testing.RpcModelTestRequest;
import com.ouroboros.rpc.dev.service.testing.RpcModelTestResult;
import com.ouroboros.rpc.dev.service.testing.RpcModelTestService;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/dev/{appName}/rpc-model/{fullName}/test")
public class RpcModelTestController {
  private final RpcModelTestService service;

  public RpcModelTestController(RpcModelTestService service) { this.service = service; }

  @PostMapping("/init-form")
  @Operation(operationId = "initializeRpcModelTest", summary = "初始化工作区 RPC 模型测试", tags = {"rpc-development"},
      description = "要求当前应用编辑权限。只分析当前应用工作区及认证依赖，返回 revision、变量与配置名称；不回显默认配置、不执行认证或目标调用。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "WebApiResult 包装的测试初始化数据；禁用模型 testable=false"),
      @ApiResponse(responseCode = "401", description = "未登录"),
      @ApiResponse(responseCode = "403", description = "无应用编辑权限")
  })
  public WebApiResult<RpcModelTestInit> initialize(
      @Parameter(description = "资源全名，不含 .rpc.json 后缀", required = true) @PathVariable String fullName,
      @RequestBody RpcModelTestInitRequest request) {
    return WebApiResult.ok(service.initialize(fullName, request.configValues()));
  }

  @PostMapping
  @Operation(operationId = "executeRpcModelTest", summary = "执行已保存工作区 RPC 模型测试", tags = {"rpc-development"},
      description = "要求应用编辑权限，仅接收 revision、variables、configValues。revision 冲突时零次目标调用；调用失败以非零 code 保留脱敏报文。目标 HTTP 状态位于 data.response.status；本接口不得自动重放。")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "WebApiResult 包装的实际调用结果，code=0 表示 HTTP 调用与解码成功，code=-1 表示测试失败"),
      @ApiResponse(responseCode = "400", description = "JSON 类型、字段或 revision 无效"),
      @ApiResponse(responseCode = "401", description = "未登录"),
      @ApiResponse(responseCode = "403", description = "无应用编辑权限")
  })
  public WebApiResult<RpcModelTestResult> execute(
      @Parameter(description = "资源全名，不含 .rpc.json 后缀", required = true) @PathVariable String fullName,
      @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "初始化 revision、已发现变量与配置覆盖；不接受 URL、模型正文或 options", required = true)
      @RequestBody RpcModelTestRequest request) {
    var result = service.execute(fullName, request);
    return result.success() ? WebApiResult.ok(result) : WebApiResult.build(-1, result.error().message(), result);
  }
}
