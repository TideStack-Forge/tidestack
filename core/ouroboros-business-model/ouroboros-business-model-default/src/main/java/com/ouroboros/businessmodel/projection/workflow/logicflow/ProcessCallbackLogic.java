package com.ouroboros.businessmodel.projection.workflow.logicflow;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.workflow.approval.callback.CallbackBinding;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class ProcessCallbackLogic extends AbstractWorkflowMPS {
  static final String LOGIC_SUFFIX = ".processCallback";

  @Override
  protected boolean shouldProcess(DefaultBusinessModel model) {
    // Disabling new commands does not discard the formal callback for in-flight executions.
    return super.shouldProcess(model) || model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId")
        .filter(value -> !value.isBlank()).isPresent();
  }

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var fullName = model.getFullName() + LOGIC_SUFFIX;
    var modelTitle = model.getTitle();
    var modelName = model.getFullName();
    var processId = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse("");
    var statusMap = model.getExtraPropDeep(Map.class, "workflow", "bindings", "0", "statusMap").orElseGet(LinkedHashMap::new);
    var statusField = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("");
    if (processId != null && !processId.isBlank()) {
      context.putModel(CallbackBinding.class, model.getFullName(),
          new CallbackBinding(processId, fullName, model.getFullName(), statusField, statusMap));
    }

    var params = new TreeMap<>(String::compareToIgnoreCase);

    params.put("fullName", fullName);
    params.put("modelTitle", modelTitle);
    params.put("modelName", modelName);
    params.put("processId", processId);
    params.put("statusMap", statusMap);
    params.put("statusField", statusField);

    var flowModel = PrototypeTemplate.generate("logic-flow/workflow/processCallback", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }
}
