package com.ouroboros.businessmodel.projection.webapi;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.AbstractDefaultBusinessModelProcessStep;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.support.BusinessModelOpenAPISupport;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

public class BatchDeleteMPS extends AbstractDefaultBusinessModelProcessStep {
  final static String LOGIC_FLOW_FIELD_NAME = "logicFlow";
  final static String API_SUFFIX = "/batch-delete";
  final static String LOGIC_SUFFIX = ".batchDelete";

  @Override
  public void process(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var isEnabled = model.getExtraPropDeep(Boolean.class, "features", "isEnabledBatchDelete").orElse(false);
    if (!isEnabled) {
      return;
    }
    var apiPath = BusinessModelUtil.buildKebabCasePath(model) + API_SUFFIX;
    var apiBuilder = SimpleWebAPIModel.builder()
        .api("POST", apiPath)
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("formatVersion", "2.0")
        .extraProp("businessModel", model.getFullName())
        .extraProp("action", "batchDelete")
        .extraProp("actionInputMapping", java.util.Map.of("params.records[].id", "${ request.body.ids }"));
    BusinessModelOpenAPISupport.applyBatchDeleteOperation(apiBuilder, model);
    var api = apiBuilder.build();
    context.putModel(WebAPIModel.class, api.getApi(), api);

  }
}
