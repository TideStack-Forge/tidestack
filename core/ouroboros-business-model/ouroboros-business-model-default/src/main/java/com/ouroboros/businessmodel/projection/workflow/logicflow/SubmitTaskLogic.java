package com.ouroboros.businessmodel.projection.workflow.logicflow;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Json;

public class SubmitTaskLogic extends AbstractWorkflowMPS {
  private static final String LOGIC_SUFFIX = ".submitTask";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var fullName = model.getFullName() + LOGIC_SUFFIX;
    var modelTitle = model.getTitle();
    var modelName = model.getFullName();
    var statusField = model.getExtraPropDeep(String.class, "workflow", "bindings", "0", "statusField").orElse("");
    var params = new TreeMap<>(String::compareToIgnoreCase);

    // 流程表单数据映射
    var formDataMode = model.getExtraPropDeep(String.class, "workflow", "formDataMode").orElse("all-fields");
    var formDataMapping = model.getExtraPropDeep(Map.class, "workflow", "formDataMapping").orElseGet(Collections::emptyMap);

    params.put("fullName", fullName);
    params.put("modelTitle", modelTitle);
    params.put("modelName", modelName);
    params.put("businessUpdateFormDataScript", businessUpdateFormDataScript(statusField));

    if ("custom".equalsIgnoreCase(formDataMode)) {
      params.put("formDataMapping", formDataMapping);
    }

    var flowModel = PrototypeTemplate.generate("logic-flow/workflow/submitTask", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }

  private String businessUpdateFormDataScript(String statusField) {
    var statusFieldLiteral = Json.toJsonString(statusField == null ? "" : statusField);
    return """
        def source = payload instanceof Map ? payload : [:]
        def formData = source.formData instanceof Map ? new java.util.LinkedHashMap(source.formData) : new java.util.LinkedHashMap()
        def statusField = %s
        if (statusField != null && statusField.trim()) {
          formData.remove(statusField)
        }
        return formData
        """.formatted(statusFieldLiteral);
  }
}
