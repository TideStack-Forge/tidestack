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

public class SubmitTaskAPI extends AbstractWorkflowMPS {
  final static String API_METHOD = "POST";
  final static String API_SUFFIX = "/{id}/process/instance/{instanceId}/task/{taskId}/{ACTION}";

  @Override
  protected void doProcess(ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var apiPath = BusinessModelUtil.buildKebabCasePath(model) + API_SUFFIX;
    var apiBuilder = SimpleWebAPIModel.builder()
        .api(API_METHOD, apiPath)
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("formatVersion", "2.0")
        .extraProp("businessModel", model.getFullName())
        .extraProp("action", "submitTask")
        .extraProp("actionInputMapping", java.util.Map.of(
            "params.record.id", "${ request.pathVariables['id'] }",
            "params.instanceId", "${ request.pathVariables['instanceId'] }",
            "params.taskId", "${ request.pathVariables['taskId'] }",
            "params.action", "${ request.pathVariables['ACTION'] }", "data", "${ request.body }"));
    BusinessModelOpenAPISupport.applySubmitTaskOperation(apiBuilder, model);
    var api = apiBuilder.build();
    context.putModel(WebAPIModel.class, api.getApi(), api);
  }
}
