package com.ouroboros.businessmodel.projection.custom;

import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.projection.ModelProjectionPipelineContext;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIScope;

public class CustomAPIMPS extends AbstractCustomMPS {
  final static String LOGIC_FLOW_FIELD_NAME = "logicFlow";

  @Override
  protected void doProcess(CustomConfig config, ModelProjectionPipelineContext<DefaultBusinessModel> context) {
    var model = context.getSourceModel();
    var name = config.getName();
    var apiConfig = config.getApi();
    if (!apiConfig.getIsEnabled()) {
      return;
    }
    if (!apiConfig.isActionBindingEnabled()) {
      // A direct @RequestMapping/@WebRequestMapping implementation owns its
      // HTTP route and must not be silently wrapped by the Action runtime.
      return;
    }
    var action = model.getBehavior().getActions().get(apiConfig.getActionName(name));
    if (action == null || !isValidBinding(action.getBinding())) {
      return;
    }

    var httpMethod = apiConfig.getHttpMethod();

    var flowModelName = model.getFullName() + "." + name;
    var hasDataModel = context.getAttribute(Boolean.class, "hasDataModel", false);
    // 逻辑流
    if (!apiConfig.isActionReference() && apiConfig.getIsGenerateImplementation() && !apiConfig.getCustomLogicFlow()) {
      var scriptLogicFlowModel = ScriptLogicModelPrototype.get(flowModelName, model)
          .configure("modelName", hasDataModel ? model.getFullName() : null)
          .configure("scriptLanguage", apiConfig.getScriptLanguage())
          .configure("sourcePointerPrefix", "/custom/" + model.getCustomConfigs().indexOf(config) + "/api")
          .configure("script", apiConfig.getScript())
          .getModel();
      context.putModel(FlowModelMeta.class, flowModelName, scriptLogicFlowModel);
    }

    // WebapiModel
    var path = buildCustomApiUrl(config, model);
    var apiModel = SimpleWebAPIModel.builder()
        .api(httpMethod, path)
        .scope(WebAPIScope.INTERNAL)
        .handlerType(HandlerType.BUSINESS_ACTION)
        .extraProp("businessModel", model.getFullName())
        .extraProp("action", action.getName())
        .extraProp("actionInputMapping", apiConfig.getActionInputMapping().orElse(java.util.Map.of()));
    if (apiConfig.getActionInputMappingFormatVersion() != null) {
      apiModel.extraProp("formatVersion", apiConfig.getActionInputMappingFormatVersion());
    }
    var builtApiModel = apiModel.build();
    context.putModel(WebAPIModel.class, builtApiModel.getApi(), builtApiModel);
  }

  private static boolean isValidBinding(com.ouroboros.businessmodel.behavior.ActionBinding binding) {
    if (binding == null) {
      return false;
    }
    return "logicflow".equalsIgnoreCase(binding.getKind())
        ? binding.getFlowName() != null && !binding.getFlowName().isBlank() && binding.getHandlerName() == null
        : "handler".equalsIgnoreCase(binding.getKind())
            && binding.getHandlerName() != null && !binding.getHandlerName().isBlank() && binding.getFlowName() == null;
  }
}
