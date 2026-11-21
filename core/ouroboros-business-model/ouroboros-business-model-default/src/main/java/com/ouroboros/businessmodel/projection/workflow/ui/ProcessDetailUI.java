package com.ouroboros.businessmodel.projection.workflow.ui;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.uimodel.SimpleUIModel;
import com.ouroboros.uimodel.UIModel;

public class ProcessDetailUI extends AbstractWorkflowMPS {
  private static final String PATH_SUFFIX = "/{id}/process-detail";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var path = BusinessModelUtil.buildKebabCasePath(model) + PATH_SUFFIX;
    var title = model.getTitle() + "审批详情";
    final var uimodel = new SimpleUIModel();
    uimodel.setTitle(title);
    uimodel.setPath(path);
    uimodel.setType("amis");
    uimodel.setExtraProp("schemaId", path);

    context.putModel(UIModel.class, path, uimodel);
  }
}
