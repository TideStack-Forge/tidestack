package com.ouroboros.businessmodel.projection.workflow.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class StartProcessLogic extends AbstractWorkflowMPS {
  private static final String LOGIC_SUFFIX = ".startProcess";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var fullName = model.getFullName() + LOGIC_SUFFIX;
    var modelTitle = model.getTitle();
    var modelName = model.getFullName();
    var processId = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "processId").orElse("");
    var pendingStatus = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusMap", "pending", "code").orElse("");
    var statusField = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("");
    var modelPath = BusinessModelUtil.buildKebabCasePath(model) + "/";
    var params = new TreeMap<>(String::compareToIgnoreCase);

    // 流程表单数据映射
    var formDataMode = model.getExtraPropDeep(String.class, "workflow", "formDataMode").orElse("all-fields");
    var formDataMapping = model.getExtraPropDeep(Map.class, "workflow", "formDataMapping").orElseGet(Collections::emptyMap);
    var sendEditUrl = model.getExtraPropDeep(Boolean.class, "workflow", "sendEditUrl").orElse(false);

    params.put("fullName", fullName);
    params.put("modelTitle", modelTitle);
    params.put("modelName", modelName);
    params.put("processId", processId);
    params.put("modelPath", modelPath);
    params.put("statusField", statusField);
    params.put("pendingStatus", pendingStatus);
    params.put("sendEditUrl", sendEditUrl);
    params.put("callbackFlow", model.getFullName() + ProcessCallbackLogic.LOGIC_SUFFIX);

    if ("custom".equalsIgnoreCase(formDataMode)) {
      params.put("formDataMapping", formDataMapping);
    }

    var flowModel = PrototypeTemplate.generate("logic-flow/workflow/startProcess", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }
}
