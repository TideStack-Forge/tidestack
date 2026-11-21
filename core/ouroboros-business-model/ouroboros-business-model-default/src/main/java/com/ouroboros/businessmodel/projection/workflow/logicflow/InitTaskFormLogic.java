package com.ouroboros.businessmodel.projection.workflow.logicflow;

import java.util.TreeMap;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.logicflow.model.FlowModelMeta;

public class InitTaskFormLogic extends AbstractWorkflowMPS {
  private static final String LOGIC_SUFFIX = ".initTaskForm";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var fullName = model.getFullName() + LOGIC_SUFFIX;
    var modelTitle = model.getTitle();
    var modelName = model.getFullName();
    var params = new TreeMap<>(String::compareToIgnoreCase);

    params.put("fullName", fullName);
    params.put("modelTitle", modelTitle);
    params.put("modelName", modelName);

    var flowModel = PrototypeTemplate.generate("logic-flow/workflow/initTaskForm", params, FlowModelMeta.class);
    context.putModel(FlowModelMeta.class, fullName, flowModel);
  }
}
