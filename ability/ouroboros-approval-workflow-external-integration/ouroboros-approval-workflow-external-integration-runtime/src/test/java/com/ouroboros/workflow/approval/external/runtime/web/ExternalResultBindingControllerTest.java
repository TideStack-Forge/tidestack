package com.ouroboros.workflow.approval.external.runtime.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.PlatformException;
import com.ouroboros.workflow.approval.external.EngineIntegrationErrorCodes;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBinding;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalResultBindingService;
import com.ouroboros.workflow.approval.FlowInstanceService;
import com.ouroboros.workflow.approval.FlowStartCommand;
import com.ouroboros.workflow.approval.FlowStartResult;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartResult;
import com.ouroboros.workflow.approval.external.runtime.web.vo.ResultOnlyStartRequest;

import io.vavr.control.Try;

/**
 * M15 结果跟踪控制器测试：发起 userId 来源、失败透传、跟踪查询命中/未命中。
 */
class ExternalResultBindingControllerTest {

  private FlowInstanceService startService;
  private ExternalResultBindingService bindingService;
  private ExternalBusinessRecordPermissionGuard businessRecordPermissionGuard;
  private ExternalResultBindingController controller;

  @BeforeEach
  void setUp() {
    startService = mock(FlowInstanceService.class);
    bindingService = mock(ExternalResultBindingService.class);
    businessRecordPermissionGuard = mock(ExternalBusinessRecordPermissionGuard.class);
    controller = new ExternalResultBindingController(startService, bindingService, businessRecordPermissionGuard);
  }

  @Test
  void startDelegatesWithCurrentUserFromSupplier() {
    when(startService.start(any(FlowStartCommand.class))).thenAnswer(inv -> {
      FlowStartCommand command = inv.getArgument(0);
      assertThat(command.userId()).isEqualTo("test-admin");
      return Try.success(new FlowStartResult("tracking-1", FlowStartResult.Status.STARTED,
          "ext-1", "http://ext/1", "fact-1", false));
    });
    ResultOnlyStartRequest request = new ResultOnlyStartRequest();
    request.setFlowKey("flow-a");
    request.setBusinessRecordType("supplier");
    request.setBusinessRecordId("sup-1");
    request.setIdempotencyKey("ik-1");

    ResultOnlyStartResult result = controller.start(request);

    assertThat(result.trackingId()).isEqualTo("tracking-1");
    verify(startService).start(any(FlowStartCommand.class));
  }

  @Test
  void startPropagatesServiceFailure() {
    when(startService.start(any(FlowStartCommand.class))).thenReturn(Try.failure(
        new BusinessException("[WORKFLOW_ROUTE_NOT_CONFIGURED] 流程未配置映射")));
    ResultOnlyStartRequest request = new ResultOnlyStartRequest();
    request.setFlowKey("flow-a");
    request.setBusinessRecordType("supplier");
    request.setBusinessRecordId("sup-1");

    assertThatThrownBy(() -> controller.start(request))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("WORKFLOW_ROUTE_NOT_CONFIGURED");
  }

  @Test
  void trackReturnsBindingView() {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId("tracking-1");
    binding.setScope("scope-a");
    binding.setBusinessRecordType("supplier");
    binding.setBusinessRecordId("sup-1");
    binding.setStatus(ExternalResultBinding.STATUS_RUNNING);
    binding.setExternalInstanceId("ext-1");
    binding.setExternalUrl("http://ext/1");
    binding.setUpdatedAt(LocalDateTime.now());
    when(bindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));

    var view = controller.track("tracking-1");

    assertThat(view.getTrackingId()).isEqualTo("tracking-1");
    assertThat(view.getStatus()).isEqualTo(ExternalResultBinding.STATUS_RUNNING);
    assertThat(view.getExternalInstanceId()).isEqualTo("ext-1");
    assertThat(view.getBusinessRecordType()).isEqualTo("supplier");
    verify(businessRecordPermissionGuard).requireView("test-admin", "scope-a", "supplier", "sup-1");
  }

  @Test
  void trackFailsClosedWhenBusinessRecordPermissionDenied() {
    ExternalResultBinding binding = new ExternalResultBinding();
    binding.setTrackingId("tracking-1");
    binding.setScope("scope-a");
    binding.setBusinessRecordType("supplier");
    binding.setBusinessRecordId("sup-1");
    when(bindingService.findByTrackingId("tracking-1")).thenReturn(Optional.of(binding));
    doThrow(new PlatformException("[" + EngineIntegrationErrorCodes.EXTERNAL_PERMISSION_DENIED
        + "] 无权限：业务记录不可见"))
        .when(businessRecordPermissionGuard).requireView("test-admin", "scope-a", "supplier", "sup-1");

    assertThatThrownBy(() -> controller.track("tracking-1"))
        .isInstanceOf(PlatformException.class)
        .hasMessageContaining("业务记录不可见");
  }

  @Test
  void trackWithUnknownTrackingIdFails() {
    when(bindingService.findByTrackingId("no-such")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> controller.track("no-such"))
        .isInstanceOf(BusinessException.class)
        .hasMessageContaining("结果绑定不存在");
  }
}
