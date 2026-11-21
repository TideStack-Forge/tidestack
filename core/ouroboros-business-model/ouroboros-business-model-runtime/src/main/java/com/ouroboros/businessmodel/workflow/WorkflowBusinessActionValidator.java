package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.BusinessModelProvider;
import com.ouroboros.businessmodel.action.BusinessActionContext;
import com.ouroboros.businessmodel.action.BusinessActionInput;
import com.ouroboros.businessmodel.action.BusinessActionInvocationValidator;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.workflow.approval.ApprovalBusinessBindingValidator;

/** Runs inside the Action's authority and transaction, before generated or overridden workflow code. */
public final class WorkflowBusinessActionValidator implements BusinessActionInvocationValidator {
  private final BusinessModelProvider models;
  private final Supplier<ApprovalBusinessBindingValidator> bindings;

  public WorkflowBusinessActionValidator(BusinessModelProvider models, Supplier<ApprovalBusinessBindingValidator> bindings) {
    this.models = models;
    this.bindings = bindings;
  }

  @Override
  public void validate(BusinessActionContext context, BusinessActionInput<?, ?> input) {
    if (List.of("startProcess", "submitTask", "initTaskForm").stream().noneMatch(context.actionName()::equalsIgnoreCase)) { return; }
    var model = models.getBusinessModel(context.modelFullName()).orElse(null);
    if (model == null
        || !model.getExtraPropDeep(Boolean.class, "workflow", "isEnabled").orElse(false)) { return; }
    String flow = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId")
        .filter(value -> !value.isBlank()).orElseThrow(() -> new BusinessException("业务工作流缺少正式流程绑定"));
    if (!(input.params() instanceof Map<?, ?> params) || !(params.get("record") instanceof Map<?, ?> record)
        || record.get("id") == null) { throw new BusinessException("工作流 Action 缺少业务对象引用"); }
    boolean start = "startProcess".equalsIgnoreCase(context.actionName());
    bindings.get().validate(flow, model.getFullName(), String.valueOf(record.get("id")),
        start ? null : required(params, "instanceId"), start ? null : required(params, "taskId"));
  }

  private String required(Map<?, ?> params, String key) {
    Object value = params.get(key);
    if (!(value instanceof String text) || text.isBlank()) { throw new BusinessException("工作流 Action 缺少 " + key); }
    return text;
  }
}
