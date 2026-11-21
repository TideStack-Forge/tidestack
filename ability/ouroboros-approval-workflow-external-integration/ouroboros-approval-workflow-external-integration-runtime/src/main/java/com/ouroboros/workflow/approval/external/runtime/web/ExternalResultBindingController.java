package com.ouroboros.workflow.approval.external.runtime.web;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowStartResult;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartResult;
import com.ouroboros.workflow.approval.external.runtime.web.vo.ResultBindingTrackingVO;
import com.ouroboros.workflow.approval.external.runtime.web.vo.ResultOnlyStartRequest;
import com.ouroboros.workflow.approval.CurrentUserSupplier;

import io.vavr.control.Try;

/**
 * 结果跟踪入口（设计文档 §结果跟踪）：结果型发起与跟踪查询。
 *
 * <p>userId 取自已登录用户（CurrentUserSupplier），不信任请求体；不生成任何本地任务。
 */
@RestController
@RequestMapping("/api/approval-workflow/external/result-bindings")
public class ExternalResultBindingController {

  private final FlowInstanceService startService;
  private final ExternalResultBindingService bindingService;
  private final ExternalBusinessRecordPermissionGuard businessRecordPermissionGuard;

  @Autowired
  public ExternalResultBindingController(
      FlowInstanceService startService,
      ExternalResultBindingService bindingService,
      ExternalBusinessRecordPermissionGuard businessRecordPermissionGuard) {
    this.startService = startService;
    this.bindingService = bindingService;
    this.businessRecordPermissionGuard = businessRecordPermissionGuard;
  }

  @PostMapping("/start")
  public ResultOnlyStartResult start(@RequestBody ResultOnlyStartRequest request) {
    String userId = requireCurrentUser();
    Try<FlowStartResult> started = startService.start(request.toFlowStartCommand(userId));
    if (started.isFailure()) {
      throw new BusinessException(started.getCause().getMessage(), started.getCause());
    }
    FlowStartResult result = started.get();
    return new ResultOnlyStartResult(result.executionId(), result.externalInstanceId(), result.externalUrl(),
        result.status().name(), result.startFactId());
  }

  @GetMapping("/{trackingId}")
  public ResultBindingTrackingVO track(@PathVariable String trackingId) {
    String userId = requireCurrentUser();
    Optional<ExternalResultBinding> binding = bindingService.findByTrackingId(trackingId);
    if (binding.isEmpty()) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_MAPPING_NOT_ACTIVE
              + "] 结果绑定不存在: " + trackingId);
    }
    ExternalResultBinding resultBinding = binding.get();
    businessRecordPermissionGuard.requireView(userId, resultBinding.getScope(),
        resultBinding.getBusinessRecordType(), resultBinding.getBusinessRecordId());
    return ResultBindingTrackingVO.fromEntity(resultBinding);
  }

  private String requireCurrentUser() {
    String userId = CurrentUserSupplier.getCurrentUserId().orElse(null);
    if (userId == null || "anonymousUser".equals(userId)) {
      throw new BusinessException(
          "[" + EngineIntegrationErrorCodes.WORKFLOW_ROUTE_NOT_CONFIGURED + "] 未登录");
    }
    return userId;
  }
}
