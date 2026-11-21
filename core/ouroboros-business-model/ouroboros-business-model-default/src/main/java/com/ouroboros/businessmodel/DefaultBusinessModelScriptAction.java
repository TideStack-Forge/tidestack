package com.ouroboros.businessmodel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.businessmodel.behavior.ActionBinding;
import com.ouroboros.businessmodel.behavior.ActionDefinition;
import com.ouroboros.businessmodel.meta.DefaultBusinessModelMeta;
import com.ouroboros.businessmodel.projection.logicflow.prototypes.ScriptLogicModelPrototype;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.util.Json;

/** Prepares a unique Action and a formal script Flow; persistence stays in the development service. */
public final class DefaultBusinessModelScriptAction implements BusinessModelScriptAction {
  public DefaultBusinessModelScriptAction() { }


  public Plan prepare(DefaultBusinessModelMeta original, BusinessModelProvider provider,
      String functionName, String actionName, String language, String script, Boolean readOnly) {
    if (!"default".equals(original.getModelType()) || !BusinessModelMetaParser.isCurrent(original)) {
      throw new BusinessException("快捷创建脚本动作须使用 2.0 默认业务模型；旧模型请先显式升级");
    }
    if (actionName == null || !actionName.matches("[A-Za-z_][A-Za-z0-9_]*")) {
      throw new BusinessException("快捷创建的动作名须为字母或下划线开头的字母、数字、下划线");
    }
    if (language == null || language.isBlank() || script == null || script.isBlank()) {
      throw new BusinessException("须提供脚本语言和完整脚本实现");
    }
    var model = new DefaultBusinessModel(original, provider);
    if (model.getBehavior().getActions().containsKey(actionName)) {
      throw new BusinessException("动作已存在，请直接选择复用: " + actionName);
    }
    var copy = (DefaultBusinessModelMeta) BusinessModelMetaParser.parse(Json.toJsonString(original)).get();
    var functions = new ArrayList<Map<String, Object>>();
    int matches = 0;
    for (Object value : copy.getExtraProp(List.class, "custom").orElse(List.of())) {
      if (!(value instanceof Map<?, ?> function)) { throw new BusinessException("custom：功能定义必须是对象"); }
      var edited = new LinkedHashMap<String, Object>();
      function.forEach((key, nested) -> edited.put(String.valueOf(key), nested));
      if (functionName != null && functionName.equals(function.get("name"))) {
        matches++;
        if (!(function.get("api") instanceof Map<?, ?> api) || !"ACTION".equals(api.get("implementationMode"))) {
          throw new BusinessException("自定义功能须先选择调用业务动作");
        }
        var editedApi = new LinkedHashMap<String, Object>();
        api.forEach((key, nested) -> editedApi.put(String.valueOf(key), nested));
        editedApi.put("action", actionName);
        if (!editedApi.containsKey("actionInputMapping")) {
          editedApi.put("actionInputMapping", Map.of("params", "${ request.params }", "data", "${ request.body }"));
        }
        edited.put("api", editedApi);
      }
      functions.add(edited);
    }
    if (matches != 1) { throw new BusinessException("须选择唯一的当前自定义功能"); }
    copy.setExtraProp("custom", functions);
    var action = new ActionDefinition();
    action.setName(actionName); action.setIsEnabled(true); action.setReadOnly(!Boolean.FALSE.equals(readOnly));
    action.setInputSchema(BusinessModelScriptAction.inputSchema());
    var binding = new ActionBinding();
    binding.setKind("logicflow"); binding.setFlowName(copy.getFullName() + "." + actionName);
    action.setBinding(binding);
    var actions = new ArrayList<>(copy.getActions()); actions.add(action); copy.setActions(actions);
    var flow = scriptFlow(binding.getFlowName(), copy.getFullName(), model, language, script);
    return new Plan(copy, flow);
  }

  /** Preview and creation use this exact formal flow prototype. */
  public FlowModelMeta scriptFlow(String flowName, String modelFullName, BusinessModel model, String language, String script) {
    return ScriptLogicModelPrototype.get(flowName, model)
        .configure("inputName", "input")
        .configure("modelName", model.getFields().isEmpty() ? null : modelFullName)
        .configure("scriptLanguage", language).configure("script", script).getModel();
  }
}
