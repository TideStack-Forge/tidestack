package com.ouroboros.businessmodel.projection.workflow.api;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.support.BusinessModelOpenAPISupport;
import com.ouroboros.businessmodel.projection.workflow.AbstractWorkflowMPS;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

public class StartProcessAPI extends AbstractWorkflowMPS {
  final static String API_SUFFIX = "/{id}/start-process";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var apiPath = BusinessModelUtil.buildKebabCasePath(model) + API_SUFFIX;
    var apiBuilder = SimpleWebAPIModel.builder()
        .api("POST", apiPath)
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("formatVersion", "2.0")
        .extraProp("businessModel", model.getFullName())
        .extraProp("action", "startProcess")
        .extraProp("actionInputMapping", java.util.Map.of(
            "params.record.id", "${ request.pathVariables['id'] }", "data", "${ request.body }"));
    BusinessModelOpenAPISupport.applyStartProcessOperation(apiBuilder, model);
    var api = apiBuilder.build();
    context.putModel(WebAPIModel.class, api.getApi(), api);
  }
}
