package com.ouroboros.workflow.approval.external.runtime.routing;

import java.util.Objects;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.event.BuiltInFlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.event.FlowTaskFactContextProvider;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalInstanceBindingService;
import com.ouroboros.workflow.approval.external.runtime.binding.ExternalTaskBindingService;
import com.ouroboros.workflow.approval.external.runtime.mapping.WorkflowEngineMappingService;
import com.ouroboros.workflow.approval.model.events.FlowInstanceEvent;
import com.ouroboros.workflow.approval.model.events.FlowTaskEvent;

/** Uses the same pinned engine route as task commands; an external task never reads Flowable variables. */
@Service
@Primary
public class RoutedFlowTaskFactContextProvider implements FlowTaskFactContextProvider {
  private final BuiltInFlowTaskFactContextProvider internal;
  private final ExternalInstanceBindingService instances;
  private final ExternalTaskBindingService tasks;
  private final ExternalBindingRouteResolver routes;

  public RoutedFlowTaskFactContextProvider(BuiltInFlowTaskFactContextProvider internal, ExternalInstanceBindingService instances,
      ExternalTaskBindingService tasks, WorkflowEngineMappingService mappings) {
    this.internal = internal;
    this.instances = instances;
    this.tasks = tasks;
    this.routes = new ExternalBindingRouteResolver(mappings);
  }

  @Override
  public void capture(FlowTaskEvent event) {
    if (event.getProjectionVersion() != null) { return; }
    var task = tasks.findByLocalId(event.getTaskId()).orElse(null);
    var instance = instances.findByLocalId(event.getInstanceId()).orElse(null);
    if (task == null && instance == null) { internal.capture(event); return; }
    if (task == null || instance == null || !Objects.equals(task.getLocalInstanceId(), instance.getLocalInstanceId())
        || !Objects.equals(task.getScope(), instance.getScope()) || !Objects.equals(task.getMappingId(), instance.getMappingId())
        || !Objects.equals(task.getMappingVersion(), instance.getMappingVersion())
        || !Objects.equals(task.getAdapterId(), instance.getAdapterId())
        || !Objects.equals(task.getConnectionId(), instance.getConnectionId())) {
      throw new BusinessException("外部任务事实缺少一致的正式实例 / 任务绑定");
    }
    if (routes.resolve(instance.getMappingId(), instance.getMappingVersion()).isBuiltIn()) { internal.capture(event); return; }
    if ((instance.getBusinessRecordType() == null) != (instance.getBusinessRecordId() == null)
        || (instance.getBusinessRecordType() != null && (instance.getBusinessRecordType().isBlank()
            || instance.getBusinessRecordId().isBlank()))) {
      throw new BusinessException("外部任务事实的持久业务归属不完整");
    }
    var context = instance.getBusinessRecordType() == null && instance.getBusinessRecordId() == null ? null
        : new FlowInstanceEvent.BusinessContext(instance.getBusinessRecordType(), instance.getBusinessRecordId());
    requireSame(event.getFlowKey(), instance.getFlowKey());
    requireSame(event.getBusinessContext(), context);
    requireSame(event.getApprovalCaseId(), instance.getApprovalCaseId());
    requireSame(event.getSubmissionRound(), instance.getSubmissionRound());
    requireSame(event.getCallbackFlow(), instance.getCallbackFlow());
    event.setFlowKey(instance.getFlowKey());
    event.setBusinessContext(context);
    event.setApprovalCaseId(instance.getApprovalCaseId());
    event.setSubmissionRound(instance.getSubmissionRound());
    event.setCallbackFlow(instance.getCallbackFlow());
    event.setProjectionVersion(1);
  }

  private void requireSame(Object requested, Object recorded) {
    if (requested != null && !Objects.equals(requested, recorded)) {
      throw new BusinessException("外部任务事实与持久业务归属冲突");
    }
  }
}
